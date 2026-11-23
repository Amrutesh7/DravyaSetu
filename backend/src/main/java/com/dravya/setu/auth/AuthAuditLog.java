package com.dravya.setu.auth;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;

@Entity
@Table(name = "auth_audit_log", indexes = {
        @Index(name = "idx_auth_audit_user_created", columnList = "user_id, created_at DESC"),
        @Index(name = "idx_auth_audit_event_created", columnList = "event, created_at DESC"),
        @Index(name = "idx_auth_audit_email_created", columnList = "email, created_at DESC"),
        @Index(name = "idx_auth_audit_ip_created", columnList = "ip, created_at DESC")
})
public class AuthAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String event;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 255)
    private String email;

    @Column(length = 45)
    private String ip;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 100)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public AuthAuditLog() {
    }

    public AuthAuditLog(String event, Long userId, String email, String ip, String userAgent,
                        boolean success, String reason, String metadata) {
        this.event = event;
        this.userId = userId;
        this.email = email;
        this.ip = ip;
        this.userAgent = userAgent;
        this.success = success;
        this.reason = reason;
        this.metadata = metadata;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getEvent() { return event; }
    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getIp() { return ip; }
    public String getUserAgent() { return userAgent; }
    public boolean isSuccess() { return success; }
    public String getReason() { return reason; }
    public String getMetadata() { return metadata; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setEvent(String event) { this.event = event; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setEmail(String email) { this.email = email; }
    public void setIp(String ip) { this.ip = ip; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public void setSuccess(boolean success) { this.success = success; }
    public void setReason(String reason) { this.reason = reason; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}