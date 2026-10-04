package com.dravya.setu.identification;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "identifications")
public class Identification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plant_id")
    private Long plantId;

    @Column(name = "request_id", length = 255)
    private String requestId;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;


    public Identification() {
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getPlantId() {
        return plantId;
    }

    public String getRequestId(){
        return requestId;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getVersion(){
        return version;
    }

    public OffsetDateTime getUpdatedAt(){
        return updatedAt;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setPlantId(Long plantId) {
        this.plantId = plantId;
    }

    public void setRequestId(String requestId){
        this.requestId = requestId;
    }

    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
