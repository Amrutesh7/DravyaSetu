package com.dravya.setu.chatbot;

public class ChatRequest {

    private String plantCode;
    private String question;
    private String language;

    public ChatRequest() {
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