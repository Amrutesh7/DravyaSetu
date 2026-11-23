package com.dravya.setu.chatbot;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "chat_sessions")
public class ChatSession {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "plant_id") private Long plantId;
    @Column(nullable = false, length = 10) private String language;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @PrePersist void create() { OffsetDateTime now = OffsetDateTime.now(); if (id == null) id = UUID.randomUUID(); createdAt = now; updatedAt = now; }
    @PreUpdate void update() { updatedAt = OffsetDateTime.now(); }
    public UUID getId() { return id; } public Long getUserId() { return userId; } public Long getPlantId() { return plantId; } public String getLanguage() { return language; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setId(UUID id) { this.id = id; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setUserId(Long userId) { this.userId = userId; } public void setPlantId(Long plantId) { this.plantId = plantId; } public void setLanguage(String language) { this.language = language; }
}
