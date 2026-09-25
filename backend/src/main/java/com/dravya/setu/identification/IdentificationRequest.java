package com.dravya.setu.identification;

public class IdentificationRequest {

    private String imageUrl;
    private String plantPart;

    public IdentificationRequest() {
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getPlantPart() {
        return plantPart;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setPlantPart(String plantPart) {
        this.plantPart = plantPart;
    }
}