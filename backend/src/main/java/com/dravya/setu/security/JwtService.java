package com.dravya.setu.security;

import com.dravya.setu.users.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", user.getRole())
                // Tokens minted before a password change carry an older version and are rejected.
                .claim("tv", user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(secretKey)
                .compact();
    }

    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean isTokenValid(String token) {
        return parse(token).isPresent();
    }

    /**
     * Verifies the signature and expiry, then exposes the claims the filter needs.
     * Returns {@link Optional#empty()} for anything that is not a currently valid token.
     */
    public Optional<JwtClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(new JwtClaims(
                    claims.getSubject(),
                    asLong(claims.get("userId")),
                    claims.get("role", String.class),
                    asInt(claims.get("tv")),
                    parseUuid(claims.getId()),
                    claims.getExpiration().toInstant()
            ));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public record JwtClaims(
            String email,
            Long userId,
            String role,
            int tokenVersion,
            UUID jti,
            Instant expiresAt) {
    }
}
