package com.dravya.setu.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

/**
 * Response body returned by Person 3's deployed knowledge service
 * ({@code POST /knowledge/chat}).
 *
 * <p>Every field is part of the published contract. {@code transcript} is {@code null} for
 * text input, and {@code voice_output} is {@code null} unless {@code generate_audio} was
 * requested.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class KnowledgeResponse {

    private String requestId;
    private String sessionId;
    private String detectedLanguage;
    private String transcript;
    private String answer;
    private String language;
    private List<KnowledgeSource> sources;
    private Map<String, Object> voiceOutput;
    private List<String> warnings;

    public KnowledgeResponse() {
    }

    public String getRequestId() {
        return requestId;
    }

    public String getSessionId() {
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

    public void setSessionId(String sessionId) {
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
