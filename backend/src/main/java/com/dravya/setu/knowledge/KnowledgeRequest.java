package com.dravya.setu.knowledge;

public class KnowledgeRequest {

    private String plantCode;
    private String question;
    private String language;

    public KnowledgeRequest() {
    }

    public KnowledgeRequest(
            String plantCode,
            String question,
            String language) {

        this.plantCode = plantCode;
        this.question = question;
        this.language = language;
    }

    public String getPlantCode() {
        return plantCode;
    }

    public String getQuestion() {
        return question;
    }

    public String getLanguage() {
        return language;
    }

    public void setPlantCode(String plantCode) {
        this.plantCode = plantCode;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}