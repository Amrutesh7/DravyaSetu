package com.dravya.setu.chatbot;

import com.dravya.setu.common.ClientErrorException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class ChatPersistenceService {
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    public ChatPersistenceService(ChatSessionRepository sessions, ChatMessageRepository messages) {
        this.sessions = sessions; this.messages = messages;
    }

    /** Only local writes are atomic; the remote knowledge request has already finished. */
    @Transactional
    public void save(UUID sessionId, boolean existing, Long userId, Long plantId, String language,
                     String question, String answer, String sourcesJson) {
        ChatSession session = existing
                ? sessions.findByIdAndUserIdForUpdate(sessionId, userId)
                    .orElseThrow(() -> ClientErrorException.notFound("Chat session not found"))
                : new ChatSession();
        if (!existing) { session.setId(sessionId); session.setUserId(userId); }
        session.setPlantId(plantId); session.setLanguage(language);
        session.setUpdatedAt(OffsetDateTime.now());
        sessions.save(session);
        saveMessage(sessionId, "USER", question, null);
        saveMessage(sessionId, "ASSISTANT", answer, sourcesJson);
        messages.flush();
    }

    private void saveMessage(UUID sessionId, String role, String content, String sourcesJson) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId); message.setRole(role);
        message.setContent(content); message.setSourcesJson(sourcesJson);
        messages.save(message);
    }
}
