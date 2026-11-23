package com.dravya.setu.auth;

import com.dravya.setu.common.ClientErrorException;
import com.dravya.setu.common.EmailNotVerifiedException;
import com.dravya.setu.common.InvalidCredentialsException;
import com.dravya.setu.mail.EmailDeliveryException;
import com.dravya.setu.mail.EmailSender;
import com.dravya.setu.mail.EmailTemplates;
import com.dravya.setu.security.JwtService;
import com.dravya.setu.security.RevokedTokenRepository;
import com.dravya.setu.users.User;
import com.dravya.setu.users.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final long USER_ID = 42L;
    private static final String OTP_SECRET = "test-secret-key-that-is-long-enough-for-hmac";

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private OtpService otpService;
    @Mock private PendingRegistrationRepository pendingRegistrationRepository;
    @Mock private EmailSender emailSender;
    @Mock private RevokedTokenRepository revokedTokenRepository;
    @Mock private AuthAuditService auditService;
    @Mock private AuthAccountLock accountLock;
    @Mock private HttpServletRequest httpRequest;

    private final EmailTemplates emailTemplates = new EmailTemplates();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(otpService.getTtlMinutes()).thenReturn(5L);
        when(otpService.getLength()).thenReturn(6);
        when(otpService.getMaxAttempts()).thenReturn(5);
        when(otpService.getMaxPerHour()).thenReturn(5);
        when(otpService.getResendCooldownSeconds()).thenReturn(60L);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(existingUser()));
        when(passwordResetTokenRepository.findUserIdByTokenHash(anyString())).thenReturn(Optional.of(USER_ID));
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("test-agent");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);

        // Use a real OtpHasher (no byte[] mock matchers needed).
        OtpHasher otpHasher = new OtpHasher(OTP_SECRET);

        authService = new AuthService(
                userRepository,
                pendingRegistrationRepository,
                passwordEncoder,
                jwtService,
                passwordResetTokenRepository,
                otpService,
                otpHasher,
                emailSender,
                emailTemplates,
                revokedTokenRepository,
                auditService,
                accountLock,
                10);
    }

    // ---------------------------------------------------------------- registration

    @Test
    void registerCreatesPendingRegistrationNotUserAndEmailsCode() {
        stubNewUser();

        authService.register(registerRequest(), httpRequest);

        // Account must NOT be created yet.
        verify(userRepository, never()).saveAndFlush(any(User.class));

        // Pending registration with hashed password must be stored.
        ArgumentCaptor<PendingRegistration> pending = ArgumentCaptor.forClass(PendingRegistration.class);
        verify(pendingRegistrationRepository).saveAndFlush(pending.capture());
        assertEquals("new@example.com", pending.getValue().getEmail());
        assertEquals("encoded-password", pending.getValue().getPasswordHash());
        assertEquals("USER", pending.getValue().getRole());

        // OTP email must be sent.
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq("new@example.com"), anyString(), html.capture());
        assertTrue(html.getValue().contains("5 minutes"));
    }

    @Test
    void registerRejectsADuplicateEmailWithoutTouchingThePassword() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(true);

        ClientErrorException error = assertThrows(
                ClientErrorException.class,
                () -> authService.register(registerRequest(), httpRequest));

        assertEquals("CONFLICT", error.getErrorCode());
        verify(pendingRegistrationRepository, never()).saveAndFlush(any(PendingRegistration.class));
        verify(passwordEncoder, never()).encode("password123");
    }

    @Test
    void registerFailsWhenTheVerificationEmailCannotBeDelivered() {
        stubNewUser();
        doThrow(new EmailDeliveryException("smtp down"))
                .when(emailSender).send(anyString(), anyString(), anyString());

        assertThrows(EmailDeliveryException.class,
                () -> authService.register(registerRequest(), httpRequest));
    }

    @Test
    void verifyEmailCreatesUserFromValidPendingRegistration() {
        PendingRegistration pending = pendingRegistration("new@example.com", "123456", 0);

        when(pendingRegistrationRepository.findByEmailForUpdate("new@example.com"))
                .thenReturn(Optional.of(pending));
        when(pendingRegistrationRepository.markConsumed(eq("new@example.com"), any()))
                .thenReturn(1);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(call -> {
            User u = call.getArgument(0);
            ReflectionTestUtils.setField(u, "id", USER_ID);
            return u;
        });

        authService.verifyEmail(verifyRequest("new@example.com", "123456"), httpRequest);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertEquals("new@example.com", saved.getValue().getEmail());
        assertEquals("encoded-password", saved.getValue().getPasswordHash());
        assertTrue(saved.getValue().isEmailVerified());
        assertNotNull(saved.getValue().getEmailVerifiedAt());
        assertNotNull(pending.getConsumedAt());
        verify(pendingRegistrationRepository).save(pending);
    }

    @Test
    void verifyEmailRejectsWrongOtpAndIncrementsAttempts() {
        PendingRegistration pending = pendingRegistration("new@example.com", "123456", 0);
        when(pendingRegistrationRepository.findByEmailForUpdate("new@example.com"))
                .thenReturn(Optional.of(pending));

        ClientErrorException error = assertThrows(ClientErrorException.class,
                () -> authService.verifyEmail(verifyRequest("new@example.com", "000000"), httpRequest));

        assertEquals("OTP_INVALID", error.getErrorCode());
        assertEquals(1, pending.getAttempts());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void verifyEmailBurnsPendingAfterMaxAttempts() {
        PendingRegistration pending = pendingRegistration("new@example.com", "123456", 4);
        when(pendingRegistrationRepository.findByEmailForUpdate("new@example.com"))
                .thenReturn(Optional.of(pending));

        ClientErrorException error = assertThrows(ClientErrorException.class,
                () -> authService.verifyEmail(verifyRequest("new@example.com", "000000"), httpRequest));

        assertEquals("OTP_ATTEMPTS_EXCEEDED", error.getErrorCode());
        assertNotNull(pending.getConsumedAt());
    }

    @Test
    void verifyEmailRejectsExpiredPending() {
        PendingRegistration pending = pendingRegistration("new@example.com", "123456", 0);
        pending.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        when(pendingRegistrationRepository.findByEmailForUpdate("new@example.com"))
                .thenReturn(Optional.of(pending));

        ClientErrorException error = assertThrows(ClientErrorException.class,
                () -> authService.verifyEmail(verifyRequest("new@example.com", "123456"), httpRequest));

        assertEquals("OTP_EXPIRED", error.getErrorCode());
        verify(pendingRegistrationRepository, never()).delete(pending);
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void verifyEmailForExistingUnverifiedUserStillWorks() {
        User user = existingUser();
        when(pendingRegistrationRepository.findByEmailForUpdate("verified@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(otpService.verify(USER_ID, OtpPurpose.EMAIL_VERIFICATION, "123456"))
                .thenReturn(verified());

        authService.verifyEmail(verifyRequest("verified@example.com", "123456"), httpRequest);

        verify(userRepository).markEmailVerified(eq(USER_ID), any());
    }

    @Test
    void verifyEmailIsIdempotentForAnAlreadyVerifiedAccount() {
        User user = existingUser();
        user.setEmailVerified(true);
        when(pendingRegistrationRepository.findByEmailForUpdate("verified@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(user));

        assertDoesNotThrow(() ->
                authService.verifyEmail(verifyRequest("verified@example.com", "123456"), httpRequest));

        verify(otpService, never()).verify(anyLong(), any(), anyString());
    }

    @Test
    void resendVerificationIssuesANewCodeForPendingRegistration() {
        PendingRegistration pending = pendingRegistration("new@example.com", "111111", 0);
        pending.setLastSentAt(OffsetDateTime.now().minusMinutes(2));
        when(pendingRegistrationRepository.findByEmailForUpdate("new@example.com"))
                .thenReturn(Optional.of(pending));
        when(pendingRegistrationRepository.countByCreatedAtAfter(any()))
                .thenReturn(0L);

        authService.resendVerification(resendRequest("new@example.com"));

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq("new@example.com"), anyString(), html.capture());
        assertNotEquals("111111", pending.getOtpHash());
        assertEquals(0, pending.getAttempts());
    }

    // ---------------------------------------------------------------- login

    @Test
    void loginReturnsATokenForAVerifiedAccount() {
        User user = existingUser();
        user.setEmailVerified(true);
        when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "stored-hash")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        AuthResponse response = authService.login(loginRequest("password123"), httpRequest);

        assertEquals("jwt-token", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(3600L, response.getExpiresIn());
    }

    @Test
    void loginRejectsAWrongPassword() {
        User user = existingUser();
        user.setEmailVerified(true);
        when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(loginRequest("wrong"), httpRequest));
        verify(jwtService, never()).generateToken(any(User.class));
    }

    @Test
    void loginRejectsAnUnknownAddressWithTheSameErrorAndComparableWork() {
        when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.empty());
        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(loginRequest("password123"), httpRequest));
        verify(passwordEncoder).matches(eq("password123"), anyString());
        verify(jwtService, never()).generateToken(any(User.class));
    }

    @Test
    void loginRejectsACorrectPasswordOnAnUnverifiedAccount() {
        User user = existingUser();
        user.setEmailVerified(false);
        when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "stored-hash")).thenReturn(true);

        assertThrows(EmailNotVerifiedException.class,
                () -> authService.login(loginRequest("password123"), httpRequest));
        verify(jwtService, never()).generateToken(any(User.class));
    }

    @Test
    void logoutRevokesOnlyThePresentedToken() {
        when(jwtService.parse("token-value")).thenReturn(Optional.of(new JwtService.JwtClaims(
                "verified@example.com", USER_ID, "USER", 0,
                java.util.UUID.randomUUID(), java.time.Instant.now().plusSeconds(600))));

        authService.logout("token-value", httpRequest);

        verify(revokedTokenRepository).revoke(any(), any());
        verify(revokedTokenRepository).deleteByExpiresAtBefore(any());
    }

    // ---------------------------------------------------------------- password reset

    @Test
    void passwordResetRequestIsSilentForAnUnknownAddress() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> authService.requestPasswordReset(forgotRequest("nobody@example.com"), httpRequest));
        verifyNoInteractions(emailSender, otpService);
    }

    @Test
    void passwordResetRequestEmailsACodeForAKnownAddress() {
        User user = existingUser();
        when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(user));
        when(otpService.issue(user, OtpPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(new OtpService.IssuedOtp(
                        "987654", OffsetDateTime.now().plusMinutes(5))));
        authService.requestPasswordReset(forgotRequest("verified@example.com"), httpRequest);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq("verified@example.com"), anyString(), html.capture());
        assertTrue(html.getValue().contains("987654"));
    }

    @Test
    void passwordResetRequestSwallowsDeliveryFailuresSoItCannotLeakExistence() {
        User user = existingUser();
        when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(user));
        when(otpService.issue(user, OtpPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(new OtpService.IssuedOtp(
                        "987654", OffsetDateTime.now().plusMinutes(5))));
        doThrow(new EmailDeliveryException("smtp down"))
                .when(emailSender).send(anyString(), anyString(), anyString());
        assertDoesNotThrow(() -> authService.requestPasswordReset(forgotRequest("verified@example.com"), httpRequest));
    }

    @Test
    void verifyResetOtpExchangesAValidCodeForASingleUseTicket() {
        User user = existingUser();
        when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(user));
        when(otpService.verify(USER_ID, OtpPurpose.PASSWORD_RESET, "123456")).thenReturn(verified());

        ResetOtpResponse response =
                authService.verifyResetOtp(resetOtpRequest("verified@example.com", "123456"), httpRequest);

        assertNotNull(response.resetToken());
        assertTrue(response.expiresAt().isAfter(OffsetDateTime.now()));
        ArgumentCaptor<PasswordResetToken> ticket = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).deleteByUserId(USER_ID);
        verify(passwordResetTokenRepository).save(ticket.capture());
        assertEquals(sha256(response.resetToken()), ticket.getValue().getTokenHash());
        assertEquals(USER_ID, ticket.getValue().getUserId());
        assertNull(ticket.getValue().getUsedAt());
    }

    @Test
    void verifyResetOtpRejectsAnInvalidCodeWithoutIssuingATicket() {
        when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(existingUser()));
        when(otpService.verify(USER_ID, OtpPurpose.PASSWORD_RESET, "000000"))
                .thenReturn(new OtpService.VerificationResult(
                        OtpService.VerificationStatus.INVALID, 4));
        assertThrows(ClientErrorException.class,
                () -> authService.verifyResetOtp(resetOtpRequest("verified@example.com", "000000"), httpRequest));
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void resetPasswordReplacesTheHashAndRevokesSessions() {
        String ticketValue = "ticket-value";
        PasswordResetToken ticket = ticket(ticketValue, OffsetDateTime.now().plusMinutes(10));
        User user = existingUser();
        user.setTokenVersion(3);
        when(passwordResetTokenRepository.findByTokenHashForUpdate(sha256(ticketValue)))
                .thenReturn(Optional.of(ticket));
        when(passwordResetTokenRepository.findUserIdByTokenHash(sha256(ticketValue))).thenReturn(Optional.of(USER_ID));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpassword1")).thenReturn("new-hash");

        authService.resetPassword(resetPasswordRequest(ticketValue, "newpassword1", "newpassword1"), httpRequest);

        assertEquals("new-hash", user.getPasswordHash());
        assertEquals(4, user.getTokenVersion());
        verify(otpService).consumeActive(USER_ID, OtpPurpose.PASSWORD_RESET);
        verify(passwordResetTokenRepository).save(ticket);
        assertNotNull(ticket.getUsedAt());
    }

    @Test
    void resetPasswordRejectsMismatchedConfirmation() {
        assertThrows(ClientErrorException.class, () -> authService.resetPassword(
                resetPasswordRequest("ticket", "newpassword1", "newpassword2"), httpRequest));
        verifyNoInteractions(passwordResetTokenRepository, userRepository);
    }

    @Test
    void resetPasswordRejectsAnExpiredTicket() {
        PasswordResetToken expired = ticket("ticket-value", OffsetDateTime.now().minusMinutes(1));
        when(passwordResetTokenRepository.findByTokenHashForUpdate(sha256("ticket-value")))
                .thenReturn(Optional.of(expired));
        when(passwordResetTokenRepository.findUserIdByTokenHash(sha256("ticket-value"))).thenReturn(Optional.of(USER_ID));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(existingUser()));
        assertThrows(ClientErrorException.class, () -> authService.resetPassword(
                resetPasswordRequest("ticket-value", "newpassword1", "newpassword1"), httpRequest));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resetPasswordRejectsAnAlreadyUsedTicket() {
        PasswordResetToken used = ticket("ticket-value", OffsetDateTime.now().plusMinutes(10));
        used.setUsedAt(OffsetDateTime.now().minusMinutes(1));
        when(passwordResetTokenRepository.findByTokenHashForUpdate(sha256("ticket-value")))
                .thenReturn(Optional.of(used));
        when(passwordResetTokenRepository.findUserIdByTokenHash(sha256("ticket-value"))).thenReturn(Optional.of(USER_ID));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(existingUser()));
        assertThrows(ClientErrorException.class, () -> authService.resetPassword(
                resetPasswordRequest("ticket-value", "newpassword1", "newpassword1"), httpRequest));
        verify(userRepository, never()).save(any(User.class));
    }

    // ---------------------------------------------------------------- fixtures

    private void stubNewUser() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(pendingRegistrationRepository.findByEmailForUpdate("new@example.com")).thenReturn(Optional.empty());
        when(pendingRegistrationRepository.findByEmailForUpdate(anyString())).thenReturn(Optional.empty());
        when(pendingRegistrationRepository.saveAndFlush(any(PendingRegistration.class)))
                .thenAnswer(call -> call.getArgument(0));
    }

    private static PendingRegistration pendingRegistration(String email, String otp, int attempts) {
        PendingRegistration p = new PendingRegistration();
        p.setEmail(email);
        p.setPasswordHash("encoded-password");
        p.setRole("USER");
        OtpHasher hasher = new OtpHasher(OTP_SECRET);
        p.setOtpHash(hasher.hashForPendingRegistration(otp, hasher.getKey()));
        p.setAttempts(attempts);
        p.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
        p.setLastSentAt(OffsetDateTime.now());
        return p;
    }

    private static User existingUser() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.setEmail("verified@example.com");
        user.setPasswordHash("stored-hash");
        user.setRole("USER");
        return user;
    }

    private static PasswordResetToken ticket(String raw, OffsetDateTime expiresAt) {
        PasswordResetToken t = new PasswordResetToken();
        t.setUserId(USER_ID);
        t.setTokenHash(sha256(raw));
        t.setExpiresAt(expiresAt);
        return t;
    }

    private static OtpService.VerificationResult verified() {
        return new OtpService.VerificationResult(OtpService.VerificationStatus.VERIFIED, 5);
    }

    private static RegisterRequest registerRequest() {
        RegisterRequest r = new RegisterRequest();
        r.setEmail("new@example.com");
        r.setPassword("password123");
        return r;
    }

    private static VerifyEmailRequest verifyRequest(String email, String otp) {
        VerifyEmailRequest r = new VerifyEmailRequest();
        r.setEmail(email);
        r.setOtp(otp);
        return r;
    }

    private static ResendVerificationRequest resendRequest(String email) {
        ResendVerificationRequest r = new ResendVerificationRequest();
        r.setEmail(email);
        return r;
    }

    private static LoginRequest loginRequest(String password) {
        LoginRequest r = new LoginRequest();
        r.setEmail("verified@example.com");
        r.setPassword(password);
        return r;
    }

    private static ForgotPasswordRequest forgotRequest(String email) {
        ForgotPasswordRequest r = new ForgotPasswordRequest();
        r.setEmail(email);
        return r;
    }

    private static VerifyResetOtpRequest resetOtpRequest(String email, String otp) {
        VerifyResetOtpRequest r = new VerifyResetOtpRequest();
        r.setEmail(email);
        r.setOtp(otp);
        return r;
    }

    private static ResetPasswordRequest resetPasswordRequest(String token, String password, String confirm) {
        ResetPasswordRequest r = new ResetPasswordRequest();
        r.setResetToken(token);
        r.setPassword(password);
        r.setConfirmPassword(confirm);
        return r;
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
