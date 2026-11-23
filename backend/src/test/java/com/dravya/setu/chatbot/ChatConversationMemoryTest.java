package com.dravya.setu.chatbot;

import com.dravya.setu.common.LanguageService;
import com.dravya.setu.knowledge.KnowledgeClient;
import com.dravya.setu.knowledge.KnowledgeRequest;
import com.dravya.setu.knowledge.KnowledgeResponse;
import com.dravya.setu.knowledge.KnowledgeSource;
import com.dravya.setu.plants.PlantRepository;
import com.dravya.setu.users.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatConversationMemoryTest {

    private static final UUID SESSION_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Mock private ChatSessionRepository sessionRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private PlantRepository plantRepository;
    @Mock private User user;

    @Test
    void prependsRecentTurnsToTheOutboundKnowledgeMessage() {

        givenOwnedSession();
        when(user.getId()).thenReturn(7L);

        when(messageRepository.findBySessionIdOrderByCreatedAtDesc(eq(SESSION_ID), any(Pageable.class)))
                .thenReturn(List.of(
                        message("ASSISTANT", "Tulasi is used for meditation and traditional medicine."),
                        message("USER", "What are the traditional uses of Tulasi?")));

        AtomicReference<KnowledgeRequest> sent = new AtomicReference<>();

        ChatService service = service(
                request -> { sent.set(request); return knowledgeResponse(); },
                true, 6, 2000);

        service.ask(chatRequest("What about its safety?"), user, "REQ_1");

        String outbound = sent.get().getMessage();

        assertTrue(outbound.startsWith("Earlier conversation (context only"), outbound);
        assertTrue(outbound.contains("user: What are the traditional uses of Tulasi?"), outbound);
        assertTrue(outbound.contains("assistant: Tulasi is used for meditation and traditional medicine."), outbound);
        assertTrue(outbound.endsWith("Current question: What about its safety?"), outbound);
        assertEquals(SESSION_ID, UUID.fromString(sent.get().getSessionId()));
    }

    @Test
    void persistsTheOriginalMessageNotTheAugmentedPrompt() {

        givenOwnedSession();
        when(user.getId()).thenReturn(7L);

        when(messageRepository.findBySessionIdOrderByCreatedAtDesc(eq(SESSION_ID), any(Pageable.class)))
                .thenReturn(List.of(message("USER", "Earlier question")));

        ChatService service = service(request -> knowledgeResponse(), true, 6, 2000);

        service.ask(chatRequest("Follow-up question"), user, "REQ_2");

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, atLeastOnce()).save(saved.capture());

        ChatMessage userMessage = saved.getAllValues().stream()
                .filter(item -> "USER".equals(item.getRole()))
                .findFirst()
                .orElseThrow();

        assertEquals("Follow-up question", userMessage.getContent());
    }

    @Test
    void leavesTheMessageUnchangedWhenTheSessionHasNoHistory() {

        givenOwnedSession();
        when(messageRepository.findBySessionIdOrderByCreatedAtDesc(eq(SESSION_ID), any(Pageable.class)))
                .thenReturn(List.of());

        AtomicReference<KnowledgeRequest> sent = new AtomicReference<>();

        ChatService service = service(
                request -> { sent.set(request); return knowledgeResponse(); },
                true, 6, 2000);

        service.ask(chatRequest("First real question"), user, "REQ_3");

        assertEquals("First real question", sent.get().getMessage());
    }

    @Test
    void doesNotLoadHistoryWhenMemoryIsDisabled() {

        givenOwnedSession();

        AtomicReference<KnowledgeRequest> sent = new AtomicReference<>();

        ChatService service = service(
                request -> { sent.set(request); return knowledgeResponse(); },
                false, 6, 2000);

        service.ask(chatRequest("A question"), user, "REQ_4");

        assertEquals("A question", sent.get().getMessage());
        verify(messageRepository, never())
                .findBySessionIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void trimsHistoryToTheCharacterBudgetNewestFirst() {

        givenOwnedSession();

        when(messageRepository.findBySessionIdOrderByCreatedAtDesc(eq(SESSION_ID), any(Pageable.class)))
                .thenReturn(List.of(
                        message("ASSISTANT", "NEWEST " + "n".repeat(300)),
                        message("ASSISTANT", "OLDER " + "o".repeat(300))));

        AtomicReference<KnowledgeRequest> sent = new AtomicReference<>();

        // Budget fits one compacted line (400-char cap) but not two.
        ChatService service = service(
                request -> { sent.set(request); return knowledgeResponse(); },
                true, 6, 450);

        service.ask(chatRequest("Question"), user, "REQ_5");

        String outbound = sent.get().getMessage();

        assertTrue(outbound.contains("NEWEST"), outbound);
        assertFalse(outbound.contains("OLDER"), outbound);
    }

    @Test
    void aFollowUpTurnRefreshesSessionLanguageAndActivityTimestamp() {

        ChatSession session = new ChatSession();
        session.setId(SESSION_ID);
        session.setUserId(7L);
        session.setLanguage("en");

        when(user.getId()).thenReturn(7L);
        when(sessionRepository.findByIdAndUserId(SESSION_ID, 7L)).thenReturn(Optional.of(session));
        when(sessionRepository.findByIdAndUserIdForUpdate(SESSION_ID, 7L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ChatSession.class))).thenAnswer(call -> call.getArgument(0));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(call -> call.getArgument(0));
        when(messageRepository.findBySessionIdOrderByCreatedAtDesc(eq(SESSION_ID), any(Pageable.class)))
                .thenReturn(List.of());

        ChatRequest request = chatRequest("Namaste");
        request.setLanguage("hi");

        ChatService service = service(request1 -> knowledgeResponse(), true, 6, 2000);
        service.ask(request, user, "REQ_7");

        ArgumentCaptor<ChatSession> saved = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionRepository).save(saved.capture());

        assertEquals("hi", saved.getValue().getLanguage());
        assertNotNull(saved.getValue().getUpdatedAt());
    }

    @Test
    void aBrandNewSessionHasNothingToRecall() {
        when(sessionRepository.findByIdAndUserId(any(), anyLong())).thenReturn(Optional.empty());
        when(sessionRepository.save(any(ChatSession.class))).thenAnswer(call -> call.getArgument(0));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(call -> call.getArgument(0));

        AtomicReference<KnowledgeRequest> sent = new AtomicReference<>();

        ChatService service = service(
                request -> { sent.set(request); return knowledgeResponse(); },
                true, 6, 2000);

        ChatRequest request = new ChatRequest();
        request.setMessage("First ever question");
        request.setLanguage("en");

        service.ask(request, user, "REQ_6");

        assertEquals("First ever question", sent.get().getMessage());
        verify(messageRepository, never())
                .findBySessionIdOrderByCreatedAtDesc(any(), any());
    }

    // ---------------------------------------------------------------- helpers

    private ChatService service(KnowledgeClient client, boolean historyEnabled,
            int messageLimit, int charLimit) {
        return new ChatService(client, new LanguageService(), sessionRepository,
                messageRepository, plantRepository, historyEnabled, messageLimit, charLimit);
    }

    private void givenOwnedSession() {
        ChatSession session = new ChatSession();
        session.setId(SESSION_ID);
        session.setUserId(7L);
        session.setLanguage("en");

        when(user.getId()).thenReturn(7L);
        when(sessionRepository.findByIdAndUserId(SESSION_ID, 7L)).thenReturn(Optional.of(session));
        when(sessionRepository.findByIdAndUserIdForUpdate(SESSION_ID, 7L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ChatSession.class))).thenAnswer(call -> call.getArgument(0));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static ChatRequest chatRequest(String message) {
        ChatRequest request = new ChatRequest();
        request.setSessionId(SESSION_ID);
        request.setMessage(message);
        request.setLanguage("en");
        return request;
    }

    private static ChatMessage message(String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(SESSION_ID);
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private static KnowledgeResponse knowledgeResponse() {
        KnowledgeResponse response = new KnowledgeResponse();
        response.setRequestId("REQ");
        response.setSessionId(SESSION_ID.toString());
        response.setDetectedLanguage("en");
        response.setAnswer("An answer.");
        response.setLanguage("en");
        response.setSources(List.of(new KnowledgeSource("SRC_001", "Tulasi", "Tulasi.pdf")));
        response.setWarnings(List.of());
        return response;
    }
}
