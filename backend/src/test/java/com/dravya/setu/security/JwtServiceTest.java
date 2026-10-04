package com.dravya.setu.security;

import com.dravya.setu.users.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET =
            "this-is-a-test-secret-key-that-is-long-enough-for-hs256";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                SECRET,
                3600000
        );
    }

    @Test
    void generateToken_shouldCreateToken() {

        User user = new User();
        user.setEmail("test@example.com");
        user.setRole("USER");

        User generatedUser = user;

        // JwtService includes userId, so provide one for the test.
        // Reflection is unnecessary because generateToken only requires
        // getId(), getEmail(), and getRole().
        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(generatedUser, 1L);
        } catch (ReflectiveOperationException e) {
            fail("Could not set test user ID", e);
        }

        String token = jwtService.generateToken(generatedUser);

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void extractEmail_shouldReturnUserEmail() {

        User user = createUser(1L, "test@example.com", "USER");

        String token = jwtService.generateToken(user);

        String email = jwtService.extractEmail(token);

        assertEquals("test@example.com", email);
    }

    @Test
    void isTokenValid_shouldReturnTrue_forValidToken() {

        User user = createUser(1L, "test@example.com", "USER");

        String token = jwtService.generateToken(user);

        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void isTokenValid_shouldReturnFalse_forTamperedToken() {

        User user = createUser(1L, "test@example.com", "USER");

        String token = jwtService.generateToken(user);

        String[] parts = token.split("\\.");

        String signature = parts[2];

        char firstCharacter = signature.charAt(0);

        char replacement =
                firstCharacter == 'A' ? 'B' : 'A';

        String tamperedSignature =
                replacement + signature.substring(1);

        String tamperedToken =
                parts[0] + "." +
                        parts[1] + "." +
                        tamperedSignature;

        assertFalse(jwtService.isTokenValid(tamperedToken));
    }

    @Test
    void isTokenValid_shouldReturnFalse_forExpiredToken() {

        SecretKey key = Keys.hmacShaKeyFor(
                SECRET.getBytes(StandardCharsets.UTF_8)
        );

        Instant now = Instant.now();

        String expiredToken = Jwts.builder()
                .subject("test@example.com")
                .claim("userId", 1L)
                .claim("role", "USER")
                .issuedAt(Date.from(now.minusSeconds(10)))
                .expiration(Date.from(now.minusSeconds(5)))
                .signWith(key)
                .compact();

        assertFalse(jwtService.isTokenValid(expiredToken));
    }

    private User createUser(
            Long id,
            String email,
            String role) {

        User user = new User();

        user.setEmail(email);
        user.setRole(role);

        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(
                    "Could not set test user ID",
                    e
            );
        }

        return user;
    }
}