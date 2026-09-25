package com.dravya.setu.ai;

public class AiRequest {

    private String imageUrl;

    public AiRequest() {
    }

    public AiRequest(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}