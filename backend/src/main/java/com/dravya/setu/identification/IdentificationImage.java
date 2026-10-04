package com.dravya.setu.identification;

import jakarta.persistence.*;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "identification_images")
public class IdentificationImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "identification_id", nullable = false)
    private Long identificationId;

    @Column(name = "image_reference", nullable = false, columnDefinition = "TEXT")
    private String imageReference;

    @Column(name = "plant_part", length = 50)
    private String plantPart;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public IdentificationImage() {
    }

    public Long getId() {
        return id;
    }

    public Long getIdentificationId() {
        return identificationId;
    }

    public String getImageReference() {
        return imageReference;
    }

    public String getPlantPart() {
        return plantPart;
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setIdentificationId(Long identificationId) {
        this.identificationId = identificationId;
    }

    public void setImageReference(String imageReference) {
        this.imageReference = imageReference;
    }

    public void setPlantPart(String plantPart) {
        this.plantPart = plantPart;
    }

    public void setSequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
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