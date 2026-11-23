package com.dravya.setu.chatbot;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "chat_messages")
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "session_id", nullable = false) private UUID sessionId;
    @Column(nullable = false, length = 20) private String role;
    @Column(name = "content", nullable = false) private String content;
    @Column(name = "sources_json", columnDefinition = "TEXT") private String sourcesJson;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @PrePersist void create() { createdAt = OffsetDateTime.now(); }
    public Long getId() { return id; }
    public String getRole() { return role; } public String getContent() { return content; }
    public String getSourcesJson() { return sourcesJson; } public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; } public void setRole(String role) { this.role = role; }
    public void setContent(String content) { this.content = content; } public void setSourcesJson(String sourcesJson) { this.sourcesJson = sourcesJson; }
}
