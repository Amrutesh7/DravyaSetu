package com.dravya.setu.ai;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Internal request sent to the AI service. Exactly one of {@code images}
 * (multipart mode) or {@code imageUrls} (URL mode) is populated.
 */
public class AiRequest {

    private List<MultipartFile> images = List.of();
    private List<String> imageUrls = List.of();
    private String requestId;
    private String plantPart;

    public AiRequest() {
    }

    public AiRequest(MultipartFile image, String requestId, String plantPart) {
        this(
                image == null ? List.of() : List.of(image),
                List.of(),
                requestId,
                plantPart);
    }

    public AiRequest(
            List<MultipartFile> images,
            List<String> imageUrls,
            String requestId,
            String plantPart) {
        this.images = images == null ? List.of() : List.copyOf(images);
        this.imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        this.requestId = requestId;
        this.plantPart = plantPart;
    }

    /**
     * Backwards-compatible accessor for the first image.
     */
    public MultipartFile getImage() {
        return images.isEmpty() ? null : images.get(0);
    }

    public List<MultipartFile> getImages() {
        return images;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getPlantPart() {
        return plantPart;
    }

    public void setImage(MultipartFile image) {
        this.images = image == null ? List.of() : List.of(image);
    }

    public void setImages(List<MultipartFile> images) {
        this.images = images == null ? List.of() : List.copyOf(images);
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public void setPlantPart(String plantPart) {
        this.plantPart = plantPart;
    }
}
