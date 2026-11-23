package com.dravya.setu.identification;

import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

public class IdentificationRequest {

    private List<MultipartFile> images = new ArrayList<>();
    private String plantPart;

    public IdentificationRequest() {
    }

    public List<MultipartFile> getImages() {
        return images;
    }

    /**
     * Backwards-compatible accessor for the first image.
     */
    public MultipartFile getImage() {
        return images.isEmpty() ? null : images.get(0);
    }

    public String getPlantPart() {
        return plantPart;
    }

    public void setImages(List<MultipartFile> images) {
        this.images = images == null ? new ArrayList<>() : new ArrayList<>(images);
    }

    public void setImage(MultipartFile image) {
        this.images = new ArrayList<>();
        if (image != null) {
            this.images.add(image);
        }
    }

    public void addImage(MultipartFile image) {
        if (image != null) {
            this.images.add(image);
        }
    }

    public void setPlantPart(String plantPart) {
        this.plantPart = plantPart;
    }
}
