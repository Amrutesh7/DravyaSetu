package com.dravya.setu.chatbot;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ChatRequest {

    private String plantId;
    private String message;
    private String language;
    /**
     * Accepted for backwards compatibility with clients built against the earlier contract.
     * Conversation continuity is carried by {@code session_id}, so this value is not forwarded.
     */
    private List<Map<String, String>> conversationContext;
    private UUID sessionId;
    /** Ask the knowledge service to synthesise a spoken version of the answer. */
    private boolean generateAudio;

    public ChatRequest() {
    }

    public String getPlantId() {
        return plantId;
    }

    public String getMessage() {
        return message;
    }

    public String getLanguage() {
        return language;
    }

    public List<Map<String, String>> getConversationContext() {
        return conversationContext;
    }
    public UUID getSessionId() { return sessionId; }
    public boolean isGenerateAudio() { return generateAudio; }

    public void setPlantId(String plantId) {
        this.plantId = plantId;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setConversationContext(List<Map<String, String>> conversationContext) {
        this.conversationContext = conversationContext;
    }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public void setGenerateAudio(boolean generateAudio) { this.generateAudio = generateAudio; }
}
