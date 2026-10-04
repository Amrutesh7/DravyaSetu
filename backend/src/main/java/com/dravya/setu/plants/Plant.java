package com.dravya.setu.plants;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "plants")
public class Plant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plant_code", nullable = false, unique = true, length = 100)
    private String plantCode;

    @Column(name = "plant_identifier", nullable = false, length = 255)
    private String plantIdentifier;

    @Column(
            name = "ayurvedic_regional_names",
            nullable = false,
            columnDefinition = "text[]"
    )
    private String[] ayurvedicRegionalNames = new String[0];

    @Column(name = "common_name", nullable = false, length = 255)
    private String commonName;

    @Column(name = "scientific_name", length = 255)
    private String scientificName;

    @Column(name = "family", length = 255)
    private String family;

    @Column(name = "description")
    private String description;

    @Column(name = "parts_used")
    private String partsUsed;

    @Column(name = "habitat")
    private String habitat;

    @Column(name = "traditional_uses")
    private String traditionalUses;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Plant() {
        // Required by JPA
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getPlantCode() {
        return plantCode;
    }

    public String getPlantIdentifier() {
        return plantIdentifier;
    }

    public String[] getAyurvedicRegionalNames() {
        return ayurvedicRegionalNames;
    }

    public void setPlantCode(String plantCode) {
        this.plantCode = plantCode;
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public String getScientificName() {
        return scientificName;
    }

    public void setScientificName(String scientificName) {
        this.scientificName = scientificName;
    }

    public String getFamily() {
        return family;
    }

    public void setPlantIdentifier(String plantIdentifier) {
        this.plantIdentifier = plantIdentifier;
    }

    public void setAyurvedicRegionalNames(
            String[] ayurvedicRegionalNames) {
        this.ayurvedicRegionalNames = ayurvedicRegionalNames;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPartsUsed() {
        return partsUsed;
    }

    public void setPartsUsed(String partsUsed) {
        this.partsUsed = partsUsed;
    }

    public String getHabitat() {
        return habitat;
    }

    public void setHabitat(String habitat) {
        this.habitat = habitat;
    }

    public String getTraditionalUses() {
        return traditionalUses;
    }

    public void setTraditionalUses(String traditionalUses) {
        this.traditionalUses = traditionalUses;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}