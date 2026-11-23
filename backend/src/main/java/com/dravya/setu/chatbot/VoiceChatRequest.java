package com.dravya.setu.chatbot;

import org.springframework.web.multipart.MultipartFile;

public class VoiceChatRequest {
    private MultipartFile audio;
    private String plantId;
    private String language;

    public MultipartFile getAudio() { return audio; }
    public String getPlantId() { return plantId; }
    public String getLanguage() { return language; }
    public void setAudio(MultipartFile audio) { this.audio = audio; }
    public void setPlantId(String plantId) { this.plantId = plantId; }
    public void setLanguage(String language) { this.language = language; }
}
