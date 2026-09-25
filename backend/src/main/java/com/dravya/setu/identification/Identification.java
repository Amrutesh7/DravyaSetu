package com.dravya.setu.identification;

import jakarta.persistence.*;
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

    @Column(nullable = false, length = 50)
    private String status;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

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

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setPlantId(Long plantId) {
        this.plantId = plantId;
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

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}