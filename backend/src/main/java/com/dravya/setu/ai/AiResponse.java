package com.dravya.setu.ai;

public class AiResponse {

    private String plantCode;
    private String status;
    private Double confidence;
    private String modelVersion;

    public AiResponse() {
    }

    public AiResponse(
            String plantCode,
            String status,
            Double confidence,
            String modelVersion) {

        this.plantCode = plantCode;
        this.status = status;
        this.confidence = confidence;
        this.modelVersion = modelVersion;
    }

    public String getPlantCode() {
        return plantCode;
    }

    public String getStatus() {
        return status;
    }

    public Double getConfidence() {
        return confidence;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setPlantCode(String plantCode) {
        this.plantCode = plantCode;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }
}