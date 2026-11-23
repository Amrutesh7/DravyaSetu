package com.dravya.setu.chatbot;

import com.dravya.setu.common.GlobalExceptionHandler;
import com.dravya.setu.common.LanguageService;
import com.dravya.setu.knowledge.MockKnowledgeClient;
import com.dravya.setu.plants.Plant;
import com.dravya.setu.plants.PlantRepository;
import com.dravya.setu.users.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatHistoryControllerTest {

    private static final UUID SESSION_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final long USER_ID = 1L;
    private static final long PLANT_DATABASE_ID = 2L;

    @Mock private ChatSessionRepository sessionRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private PlantRepository plantRepository;
    @Mock private Plant plant;
    @Mock private User user;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(user.getId()).thenReturn(USER_ID);
        when(plant.getPlantCode()).thenReturn("PLANT_001");

        ChatService service = new ChatService(
                new MockKnowledgeClient(), new LanguageService(),
                sessionRepository, messageRepository, plantRepository,
                true, 6, 2000);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ChatController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticatedUserResolver(user))
                .build();
    }

    @Test
    void listsCallerSessionsWithTheSharedPlantCode() throws Exception {
        when(plantRepository.findById(PLANT_DATABASE_ID)).thenReturn(Optional.of(plant));
        when(sessionRepository.findByUserIdOrderByUpdatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(session()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/chat/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].session_id").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.content[0].plant_id").value("PLANT_001"))
                .andExpect(jsonPath("$.content[0].language").value("en"));
    }

    @Test
    void returnsPersistedMessagesOldestFirstWithSources() throws Exception {
        when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID))
                .thenReturn(Optional.of(session()));
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(eq(SESSION_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        message("USER", "What are the traditional uses of Tulasi?", null),
                        message("ASSISTANT", "Tulasi is used for meditation.",
                                """
                                [{"source_id":"SRC_001","title":"Tulasi","reference":"Tulasi.pdf"}]
                                """)), PageRequest.of(0, 50), 2));

        mockMvc.perform(get("/chat/sessions/" + SESSION_ID + "/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].role").value("user"))
                .andExpect(jsonPath("$.content[0].content").value("What are the traditional uses of Tulasi?"))
                .andExpect(jsonPath("$.content[0].sources").isEmpty())
                .andExpect(jsonPath("$.content[1].role").value("assistant"))
                .andExpect(jsonPath("$.content[1].sources[0].source_id").value("SRC_001"))
                .andExpect(jsonPath("$.content[1].sources[0].title").value("Tulasi"))
                .andExpect(jsonPath("$.content[1].sources[0].reference").value("Tulasi.pdf"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void returnsASingleCallerOwnedSession() throws Exception {
        when(plantRepository.findById(PLANT_DATABASE_ID)).thenReturn(Optional.of(plant));
        when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID))
                .thenReturn(Optional.of(session()));

        mockMvc.perform(get("/chat/sessions/" + SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session_id").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.plant_id").value("PLANT_001"));
    }

    @Test
    void hidesSessionsOwnedByAnotherUser() throws Exception {
        when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/chat/sessions/" + SESSION_ID + "/messages"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void rejectsOutOfRangePaging() throws Exception {
        mockMvc.perform(get("/chat/sessions").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ---------------------------------------------------------------- helpers

    private ChatSession session() {
        ChatSession session = new ChatSession();
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setPlantId(PLANT_DATABASE_ID);
        session.setLanguage("en");
        return session;
    }

    private static ChatMessage message(String role, String content, String sourcesJson) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(SESSION_ID);
        message.setRole(role);
        message.setContent(content);
        message.setSourcesJson(sourcesJson);
        return message;
    }

    /** Supplies the authenticated principal that Spring Security would normally inject. */
    private record AuthenticatedUserResolver(User user) implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return User.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return user;
        }
    }
}
