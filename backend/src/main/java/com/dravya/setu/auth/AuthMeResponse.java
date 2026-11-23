package com.dravya.setu.auth;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;

/**
 * Minimal identity information for an authenticated caller. Lets a client know its role and
 * whether the guarded area may be entered, without decoding the JWT itself.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AuthMeResponse(
        Long id,
        String email,
        String role,
        boolean emailVerified,
        OffsetDateTime createdAt) {
}
