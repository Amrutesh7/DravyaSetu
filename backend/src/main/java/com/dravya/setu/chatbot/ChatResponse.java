package com.dravya.setu.chatbot;

import com.dravya.setu.knowledge.KnowledgeSource;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public chat response returned to Web and Mobile clients.
 *
 * <p>It mirrors the knowledge service response so that Person 4 can render the answer,
 * detected language, grounded sources and optional voice metadata without a second call.</p>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ChatResponse {

    private String requestId;
    private UUID sessionId;
    private String detectedLanguage;
    private String transcript;
    private String answer;
    private String language;
    private List<KnowledgeSource> sources;
    private Map<String, Object> voiceOutput;
    private List<String> warnings;

    public ChatResponse() {
    }

    public ChatResponse(
            String requestId,
            UUID sessionId,
            String detectedLanguage,
            String transcript,
            String answer,
            String language,
            List<KnowledgeSource> sources,
            Map<String, Object> voiceOutput,
            List<String> warnings) {

        this.requestId = requestId;
        this.sessionId = sessionId;
        this.detectedLanguage = detectedLanguage;
        this.transcript = transcript;
        this.answer = answer;
        this.language = language;
        this.sources = sources;
        this.voiceOutput = voiceOutput;
        this.warnings = warnings;
    }

    public String getRequestId() {
        return requestId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getDetectedLanguage() {
        return detectedLanguage;
    }

    public String getTranscript() {
        return transcript;
    }

    public String getAnswer() {
        return answer;
    }

    public String getLanguage() {
        return language;
    }

    public List<KnowledgeSource> getSources() {
        return sources;
    }

    public Map<String, Object> getVoiceOutput() {
        return voiceOutput;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public void setDetectedLanguage(String detectedLanguage) {
        this.detectedLanguage = detectedLanguage;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setSources(List<KnowledgeSource> sources) {
        this.sources = sources;
    }

    public void setVoiceOutput(Map<String, Object> voiceOutput) {
        this.voiceOutput = voiceOutput;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
