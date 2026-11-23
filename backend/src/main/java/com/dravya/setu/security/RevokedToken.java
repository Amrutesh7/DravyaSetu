package com.dravya.setu.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A JWT that was logged out before its natural expiry. Rows are purged opportunistically;
 * once a token expires it is rejected by signature/expiry validation anyway.
 */
@Entity
@Table(name = "revoked_tokens")
public class RevokedToken {

    @Id
    @Column(name = "jti", nullable = false)
    private UUID jti;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public RevokedToken() {
    }

    public RevokedToken(UUID jti, OffsetDateTime expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getJti() {
        return jti;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
