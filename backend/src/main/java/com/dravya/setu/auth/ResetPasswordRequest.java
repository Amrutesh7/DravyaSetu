package com.dravya.setu.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Third step of the password reset flow. {@code reset_token} is the single-use ticket returned
 * by {@code POST /auth/verify-reset-otp}; the OTP itself is never accepted here.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ResetPasswordRequest {

    @NotBlank(message = "Reset token is required")
    @JsonAlias("token")
    private String resetToken;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    @Pattern(regexp = ".*[A-Za-z].*", message = "Password must contain a letter")
    @Pattern(regexp = ".*\\d.*", message = "Password must contain a number")
    private String password;

    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;

    public String getResetToken() {
        return resetToken;
    }

    public String getPassword() {
        return password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
