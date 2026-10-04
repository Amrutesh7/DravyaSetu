package com.dravya.setu.identification;

import org.springframework.web.multipart.MultipartFile;

public class IdentificationRequest {

    private MultipartFile image;
    private String plantPart;

    public IdentificationRequest() {
    }

    public MultipartFile getImage() {
        return image;
    }

    public String getPlantPart() {
        return plantPart;
    }

    public void setImage(MultipartFile image) {
        this.image = image;
    }

    public void setPlantPart(String plantPart) {
        this.plantPart = plantPart;
    }
}