package com.dravya.setu.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, UUID> {

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            INSERT INTO revoked_tokens(jti, expires_at) VALUES (:jti, :expiresAt)
            ON CONFLICT (jti) DO NOTHING
            """, nativeQuery = true)
    int revoke(@org.springframework.data.repository.query.Param("jti") UUID jti,
               @org.springframework.data.repository.query.Param("expiresAt") OffsetDateTime expiresAt);

    long deleteByExpiresAtBefore(OffsetDateTime cutoff);
}
