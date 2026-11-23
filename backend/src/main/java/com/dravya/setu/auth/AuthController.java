package com.dravya.setu.auth;

import com.dravya.setu.users.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Creates a pending account and emails a 6-digit verification code. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {

        authService.register(request, httpRequest);
    }

    /** Confirms the emailed code and activates the account. Idempotent. */
    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request, HttpServletRequest httpRequest) {

        authService.verifyEmail(request, httpRequest);
    }

    /** Requests a new verification code. Always answers 202. */
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {

        authService.resendVerification(request);
    }

    /** Returns a bearer token for a verified account with correct credentials. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest);
    }

    /** Revokes the token used for this request. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                       String authorization, HttpServletRequest httpRequest) {

        authService.logout(stripBearer(authorization), httpRequest);
    }

    /** Identity, role and verification state of the authenticated caller. */
    @GetMapping("/me")
    public AuthMeResponse me(@AuthenticationPrincipal User user) {

        return new AuthMeResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.isEmailVerified(),
                user.getCreatedAt());
    }

    /** Step 1 of the reset flow. Always answers 202, whether or not the address exists. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {

        authService.requestPasswordReset(request, httpRequest);
    }

    /** Step 2: exchange a valid reset code for a single-use reset ticket. */
    @PostMapping("/verify-reset-otp")
    public ResetOtpResponse verifyResetOtp(
            @Valid @RequestBody VerifyResetOtpRequest request, HttpServletRequest httpRequest) {

        return authService.verifyResetOtp(request, httpRequest);
    }

    /** Step 3: set the new password and sign out every existing session. */
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {

        authService.resetPassword(request, httpRequest);
    }

    private static String stripBearer(String authorization) {
        return authorization != null && authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length())
                : null;
    }
}
