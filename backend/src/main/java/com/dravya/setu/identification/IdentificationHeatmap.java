package com.dravya.setu.identification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * A heatmap is always linked to the identification and, when known, to the
 * original image it explains. Either {@code storageKey} (archived in B2) or
 * {@code sourceUrl} (AI fallback URL) is present.
 */
@Entity
@Table(name = "identification_heatmaps")
public class IdentificationHeatmap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "identification_id", nullable = false)
    private Long identificationId;

    @Column(name = "identification_image_id", nullable = false)
    private Long identificationImageId;

    @Column(name = "storage_key", columnDefinition = "TEXT")
    private String storageKey;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "content_type", length = 100)
    private String contentType;

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

    public IdentificationHeatmap() {
    }

    public Long getId() {
        return id;
    }

    public Long getIdentificationId() {
        return identificationId;
    }

    public Long getIdentificationImageId() {
        return identificationImageId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getContentType() {
        return contentType;
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

    public void setIdentificationImageId(Long identificationImageId) {
        this.identificationImageId = identificationImageId;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
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
