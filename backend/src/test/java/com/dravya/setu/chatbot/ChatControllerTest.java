package com.dravya.setu.chatbot;

import com.dravya.setu.common.GlobalExceptionHandler;
import com.dravya.setu.common.LanguageService;
import com.dravya.setu.knowledge.MockKnowledgeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ChatService service = new ChatService(new MockKnowledgeClient(), new LanguageService());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ChatController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void postChat_shouldReturnTheDeployedKnowledgeShape() throws Exception {
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "What are its traditional uses?",
                                  "plant_id": "PLANT_001",
                                  "language": "en"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request_id").isNotEmpty())
                .andExpect(jsonPath("$.session_id").isNotEmpty())
                .andExpect(jsonPath("$.detected_language").value("en"))
                .andExpect(jsonPath("$.transcript").value(nullValue()))
                .andExpect(jsonPath("$.answer").isNotEmpty())
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.sources[0].source_id").value("SRC_MOCK_001"))
                .andExpect(jsonPath("$.sources[0].title").isNotEmpty())
                .andExpect(jsonPath("$.sources[0].reference").isNotEmpty())
                .andExpect(jsonPath("$.voice_output").value(nullValue()))
                .andExpect(jsonPath("$.warnings").isEmpty())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void postChat_shouldRejectAnUnsupportedLanguage() throws Exception {
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "Hello", "language": "zz"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").isNotEmpty());
    }

    @Test
    void postChatVoice_shouldReturn501BecauseTheServiceHasNoSpeechToText() throws Exception {
        mockMvc.perform(multipart("/chat/voice")
                        .file(new MockMultipartFile(
                                "audio", "question.wav", "audio/wav", new byte[] {1, 2})))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.error.code").value("VOICE_INPUT_UNSUPPORTED"))
                .andExpect(jsonPath("$.error.message").value(containsString("generate_audio")));
    }
}
