package com.dravya.setu.identification;

import jakarta.persistence.*;

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

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

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

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}