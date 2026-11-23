package com.dravya.setu.knowledge;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Request body for Person 3's deployed knowledge service
 * ({@code POST /knowledge/chat}).
 *
 * <p>The field names and the {@code input_type} literal match the service's published
 * OpenAPI schema, so this class is the single place where the wire contract lives.</p>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class KnowledgeRequest {

    /** The deployed service only accepts text input on this route. */
    public static final String INPUT_TYPE_TEXT = "text";

    private String requestId;
    private String sessionId;
    private String message;
    private final String inputType = INPUT_TYPE_TEXT;
    private String inputLanguage;
    private String responseLanguage;
    private String plantId;
    private boolean generateAudio;

    public KnowledgeRequest() {
    }

    public KnowledgeRequest(
            String requestId,
            String sessionId,
            String message,
            String inputLanguage,
            String responseLanguage,
            String plantId,
            boolean generateAudio) {

        this.requestId = requestId;
        this.sessionId = sessionId;
        this.message = message;
        this.inputLanguage = inputLanguage;
        this.responseLanguage = responseLanguage;
        this.plantId = plantId;
        this.generateAudio = generateAudio;
    }

    /**
     * Convenience constructor for the common case where the caller's language is both
     * the input and the requested response language, and audio output is not needed.
     */
    public KnowledgeRequest(
            String requestId,
            String sessionId,
            String message,
            String language,
            String plantId) {

        this(requestId, sessionId, message, language, language, plantId, false);
    }

    public String getRequestId() {
        return requestId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getMessage() {
        return message;
    }

    public String getInputType() {
        return inputType;
    }

    public String getInputLanguage() {
        return inputLanguage;
    }

    public String getResponseLanguage() {
        return responseLanguage;
    }

    public String getPlantId() {
        return plantId;
    }

    public boolean isGenerateAudio() {
        return generateAudio;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setInputLanguage(String inputLanguage) {
        this.inputLanguage = inputLanguage;
    }

    public void setResponseLanguage(String responseLanguage) {
        this.responseLanguage = responseLanguage;
    }

    public void setPlantId(String plantId) {
        this.plantId = plantId;
    }

    public void setGenerateAudio(boolean generateAudio) {
        this.generateAudio = generateAudio;
    }
}
