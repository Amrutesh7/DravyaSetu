package com.dravya.setu.identification;

public class IdentificationResponse {

    private Long identificationId;
    private String plantCode;
    private String status;
    private Double confidence;
    private String modelVersion;

    public IdentificationResponse() {
    }

    public IdentificationResponse(
            Long identificationId,
            String plantCode,
            String status,
            Double confidence,
            String modelVersion) {

        this.identificationId = identificationId;
        this.plantCode = plantCode;
        this.status = status;
        this.confidence = confidence;
        this.modelVersion = modelVersion;
    }

    public Long getIdentificationId() {
        return identificationId;
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

    public void setIdentificationId(Long identificationId) {
        this.identificationId = identificationId;
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