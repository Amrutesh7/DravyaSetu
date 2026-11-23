package com.dravya.setu.auth;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "pending_registrations",
        uniqueConstraints = @UniqueConstraint(name = "uq_pending_registrations_email", columnNames = "email"),
        indexes = {
                @Index(name = "idx_pending_registrations_expires_at", columnList = "expires_at"),
                @Index(name = "idx_pending_registrations_email", columnList = "email")
        }
)
public class PendingRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(name = "otp_hash", nullable = false, length = 64)
    private String otpHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "last_sent_at", nullable = false)
    private OffsetDateTime lastSentAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "send_window_started_at", nullable = false)
    private OffsetDateTime sendWindowStartedAt;

    @Column(name = "send_count", nullable = false)
    private int sendCount;

    public boolean canSend(OffsetDateTime now, int limit) {
        return sendWindowStartedAt == null || !sendWindowStartedAt.plusHours(1).isAfter(now)
                || sendCount < limit;
    }

    public void recordSend(OffsetDateTime now) {
        if (sendWindowStartedAt == null || !sendWindowStartedAt.plusHours(1).isAfter(now)) {
            sendWindowStartedAt = now;
            sendCount = 0;
        }
        sendCount++;
        lastSentAt = now;
    }

    public PendingRegistration() {
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        if (sendWindowStartedAt == null) sendWindowStartedAt = now;
        if (this.lastSentAt == null) {
            this.lastSentAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public String getOtpHash() {
        return otpHash;
    }

    public int getAttempts() {
        return attempts;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getConsumedAt() {
        return consumedAt;
    }

    public OffsetDateTime getLastSentAt() {
        return lastSentAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setOtpHash(String otpHash) {
        this.otpHash = otpHash;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setConsumedAt(OffsetDateTime consumedAt) {
        this.consumedAt = consumedAt;
    }

    public void setLastSentAt(OffsetDateTime lastSentAt) {
        this.lastSentAt = lastSentAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
