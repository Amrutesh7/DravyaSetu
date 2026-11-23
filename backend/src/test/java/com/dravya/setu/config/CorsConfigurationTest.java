package com.dravya.setu.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigurationTest {
    @Test
    void allowsOnlyConfiguredDevelopmentOriginsWithCredentials() {
        SecurityConfig config = new SecurityConfig(null,
                List.of("http://localhost:*", "http://127.0.0.1:*"));
        CorsConfiguration cors = config.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/chat"));

        assertEquals("http://localhost:5173", cors.checkOrigin("http://localhost:5173"));
        assertEquals("http://127.0.0.1:3000", cors.checkOrigin("http://127.0.0.1:3000"));
        assertEquals(null, cors.checkOrigin("https://untrusted.example"));
        assertTrue(cors.getAllowCredentials());
    }
}
