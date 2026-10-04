package com.dravya.setu.knowledge;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class KnowledgeResponse {

    private String requestId;
    private String answer;
    private String language;
    private List<Map<String, Object>> sources;
    private Map<String, Object> voiceOutput;
    private List<String> warnings;
    private List<String> errors;

    public KnowledgeResponse() {
    }

    public String getRequestId() {
        return requestId;
    }

    public String getAnswer() {
        return answer;
    }

    public String getLanguage() {
        return language;
    }

    public List<Map<String, Object>> getSources() {
        return sources;
    }

    public Map<String, Object> getVoiceOutput() {
        return voiceOutput;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setSources(
            List<Map<String, Object>> sources) {
        this.sources = sources;
    }

    public void setVoiceOutput(
            Map<String, Object> voiceOutput) {
        this.voiceOutput = voiceOutput;
    }

    public void setWarnings(
            List<String> warnings) {
        this.warnings = warnings;
    }

    public void setErrors(
            List<String> errors) {
        this.errors = errors;
    }
}