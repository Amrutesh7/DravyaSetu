package com.dravya.setu.security;

import com.dravya.setu.users.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET =
            "unit-test-secret-that-is-at-least-32-characters-long";

    @Test
    void roundTripsIdentityRoleVersionAndTokenId() {

        JwtService service = new JwtService(SECRET, 60_000);
        User user = user(9L, "user@example.com", "ADMIN", 3);

        String token = service.generateToken(user);
        JwtService.JwtClaims claims = service.parse(token).orElseThrow();

        assertEquals("user@example.com", claims.email());
        assertEquals(9L, claims.userId());
        assertEquals("ADMIN", claims.role());
        assertEquals(3, claims.tokenVersion());
        assertNotNull(claims.jti());
        assertTrue(claims.expiresAt().isAfter(java.time.Instant.now()));
        assertEquals(60L, service.getExpirationSeconds());
    }

    @Test
    void rejectsAnExpiredToken() {

        JwtService service = new JwtService(SECRET, -1_000);

        String token = service.generateToken(user(1L, "user@example.com", "USER", 0));

        assertTrue(service.parse(token).isEmpty());
        assertFalse(service.isTokenValid(token));
    }

    @Test
    void rejectsATamperedToken() {

        JwtService service = new JwtService(SECRET, 60_000);
        String token = service.generateToken(user(1L, "user@example.com", "USER", 0));

        String tampered = token.substring(0, token.length() - 2) + "xy";

        assertTrue(service.parse(tampered).isEmpty());
    }

    @Test
    void rejectsATokenSignedWithAnotherSecret() {

        JwtService issuer = new JwtService(SECRET, 60_000);
        JwtService verifier = new JwtService(
                "another-secret-that-is-also-at-least-32-characters", 60_000);

        String token = issuer.generateToken(user(1L, "user@example.com", "USER", 0));

        assertTrue(verifier.parse(token).isEmpty());
    }

    @Test
    void issuesADifferentTokenIdEachTime() {

        JwtService service = new JwtService(SECRET, 60_000);
        User user = user(1L, "user@example.com", "USER", 0);

        Optional<JwtService.JwtClaims> first = service.parse(service.generateToken(user));
        Optional<JwtService.JwtClaims> second = service.parse(service.generateToken(user));

        assertNotEquals(first.orElseThrow().jti(), second.orElseThrow().jti());
    }

    private static User user(Long id, String email, String role, int tokenVersion) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setTokenVersion(tokenVersion);
        user.setEmailVerified(true);
        return user;
    }
}
