package com.dravya.setu.auth;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;

/**
 * Short-lived authorization to set a new password, issued only after the reset code was
 * verified. Single use; it replaces the code rather than extending its life.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ResetOtpResponse(String resetToken, OffsetDateTime expiresAt) {
}
