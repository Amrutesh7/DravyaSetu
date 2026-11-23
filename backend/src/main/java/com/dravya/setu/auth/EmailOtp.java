package com.dravya.setu.auth;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * A one-time code, stored only as an HMAC-SHA-256 digest bound to the user and purpose.
 * The plaintext code exists in memory long enough to be emailed and is never persisted
 * or logged.
 */
@Entity
@Table(name = "email_otps")
public class EmailOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OtpPurpose purpose;

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

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (lastSentAt == null) {
            lastSentAt = createdAt;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public OtpPurpose getPurpose() {
        return purpose;
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

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setPurpose(OtpPurpose purpose) {
        this.purpose = purpose;
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
}
