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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

/**
 * Registration, verification, login, logout and the three-step password reset.
 *
 * <p>Security invariants:</p>
 * <ul>
 *   <li>passwords are BCrypt-hashed and never compared or stored in plaintext;</li>
 *   <li>OTPs are stored as keyed digests (see {@link OtpHasher}) and are never logged;</li>
 *   <li>an unverified account cannot log in, even with the correct password;</li>
 *   <li>a password change bumps {@code token_version}, invalidating every existing JWT;</li>
 *   <li>responses that could reveal whether an address is registered are identical either way.</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final OtpService otpService;
    private final OtpHasher otpHasher;
    private final EmailSender emailSender;
    private final EmailTemplates emailTemplates;
    private final RevokedTokenRepository revokedTokenRepository;
    private final AuthAuditService auditService;
    private final AuthAccountLock accountLock;
    private final long resetTicketTtlMinutes;

    /**
     * Hash compared against when no account exists, so a wrong email costs the same time as a
     * wrong password. Generated once at startup, never used to authenticate anybody.
     */
    private final String timingEqualisationHash;

    public AuthService(
            UserRepository userRepository,
            PendingRegistrationRepository pendingRegistrationRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            PasswordResetTokenRepository passwordResetTokenRepository,
            OtpService otpService,
            OtpHasher otpHasher,
            EmailSender emailSender,
            EmailTemplates emailTemplates,
            RevokedTokenRepository revokedTokenRepository,
            AuthAuditService auditService,
            AuthAccountLock accountLock,
            @Value("${app.password-reset.ticket-ttl-minutes:10}") long resetTicketTtlMinutes
    ) {
        this.userRepository = userRepository;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.otpService = otpService;
        this.otpHasher = otpHasher;
        this.emailSender = emailSender;
        this.emailTemplates = emailTemplates;
        this.revokedTokenRepository = revokedTokenRepository;
        this.auditService = auditService;
        this.accountLock = accountLock;
        this.resetTicketTtlMinutes = resetTicketTtlMinutes;
        this.timingEqualisationHash = passwordEncoder.encode(randomToken());
    }

    // ---------------------------------------------------------------- registration

    /**
     * Creates a pending registration and emails a verification code.
     * Account is only created after OTP verification.
     */
    @Transactional
    public void register(RegisterRequest request, HttpServletRequest httpRequest) {

        String email = normalise(request.getEmail());
        accountLock.acquire(email);
        String ip = clientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");

        // Check if email already has a verified account
        if (userRepository.existsByEmail(email)) {
            auditService.registerAttempt(email, ip, ua, false, "EMAIL_EXISTS");
            throw ClientErrorException.conflict(
                    "Email already registered. Verify it or request a new code.");
        }

        // Check if there's an existing pending registration for this email
        Optional<PendingRegistration> existingPending = pendingRegistrationRepository.findByEmailForUpdate(email);
        if (existingPending.isPresent()) {
            PendingRegistration pending = existingPending.get();
            // Reuse inactive rows so the per-address send budget survives renewal.
            if (pending.getExpiresAt().isAfter(OffsetDateTime.now()) && pending.getConsumedAt() == null) {
                // Still valid pending registration - don't reveal it exists
                auditService.registerAttempt(email, ip, ua, true, null);
                return;
            }
        }

        // Create pending registration with hashed password
        PendingRegistration pending = existingPending.orElseGet(PendingRegistration::new);
        if (!pending.canSend(OffsetDateTime.now(), otpService.getMaxPerHour())) {
            throw new ClientErrorException("RATE_LIMITED", "Too many codes requested. Try again later.");
        }
        pending.setEmail(email);
        pending.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        pending.setRole("USER");
        pending.setExpiresAt(OffsetDateTime.now().plusMinutes(otpService.getTtlMinutes()));

        // Generate OTP and store its hash directly in pending registration
        String otpCode = generateOtpCode();
        String otpHash = otpHasher.hashForPendingRegistration(otpCode, otpHasher.getKey());
        pending.setOtpHash(otpHash);
        pending.setAttempts(0);
        pending.setConsumedAt(null);
        pending.recordSend(OffsetDateTime.now());

        pendingRegistrationRepository.saveAndFlush(pending);

        // Send OTP email
        EmailTemplates.EmailContent content =
                emailTemplates.verificationOtp(otpCode, otpService.getTtlMinutes());

        // Intentionally not caught: an undelivered code must fail the whole registration.
        emailSender.send(email, content.subject(), content.html());

        auditService.registerAttempt(email, ip, ua, true, null);
    }

    private String generateOtpCode() {
        StringBuilder code = new StringBuilder(otpService.getLength());
        for (int i = 0; i < otpService.getLength(); i++) {
            code.append(RANDOM.nextInt(10));
        }
        return code.toString();
    }

    private static String clientIp(HttpServletRequest request) {
        Object trusted = request.getAttribute(com.dravya.setu.security.RateLimitFilter.CLIENT_IP_ATTRIBUTE);
        return trusted instanceof String ip ? ip : request.getRemoteAddr();
    }

    // ---------------------------------------------------------------- email verification

    @Transactional(noRollbackFor = ClientErrorException.class)
    public void verifyEmail(VerifyEmailRequest request, HttpServletRequest httpRequest) {

        String email = normalise(request.getEmail());
        accountLock.acquire(email);
        String ip = clientIp(httpRequest);

        // First, check for pending registration (new flow: OTP before account creation)
        if (userRepository.findByEmail(email).filter(User::isEmailVerified).isPresent()) return;
        Optional<PendingRegistration> pendingOpt = pendingRegistrationRepository.findByEmailForUpdate(email);
        if (pendingOpt.isPresent() && pendingOpt.get().getConsumedAt() == null) {
            PendingRegistration pending = pendingOpt.get();

            // Check if expired
            if (!pending.getExpiresAt().isAfter(OffsetDateTime.now())) {
                // Fall through to timing-safe failure
                requireVerified(new OtpService.VerificationResult(
                        OtpService.VerificationStatus.EXPIRED, 0));
                return;
            }

            // Verify OTP against pending registration
            String submittedOtp = request.getOtp();
            if (OtpHasher.matchesForPendingRegistration(
                    submittedOtp, pending.getOtpHash(), otpHasher.getKey())) {

                // OTP valid - create the user account
                User user = new User();
                user.setEmail(pending.getEmail());
                user.setPasswordHash(pending.getPasswordHash());
                user.setRole(pending.getRole());
                user.setEmailVerified(true);
                user.setEmailVerifiedAt(OffsetDateTime.now());

                user = userRepository.saveAndFlush(user);

                // Mark pending registration as consumed
                pending.setConsumedAt(OffsetDateTime.now());
                pendingRegistrationRepository.save(pending);

                auditService.emailVerified(user.getId(), user.getEmail(), ip);
                return;
            }

            // OTP invalid - increment attempts
            int newAttempts = pending.getAttempts() + 1;
            pending.setAttempts(newAttempts);

            if (newAttempts >= otpService.getMaxAttempts()) {
                // Max attempts reached - consume the pending registration
                pending.setConsumedAt(OffsetDateTime.now());
                pendingRegistrationRepository.save(pending);
                requireVerified(new OtpService.VerificationResult(
                        OtpService.VerificationStatus.ATTEMPTS_EXCEEDED, 0));
                return;
            }

            pendingRegistrationRepository.save(pending);
            requireVerified(new OtpService.VerificationResult(
                    OtpService.VerificationStatus.INVALID, otpService.getMaxAttempts() - newAttempts));
            return;
        }

        // No pending registration - check for existing unverified user (backwards compatibility)
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // Same failure and comparable timing as a wrong code for an existing account.
            requireVerified(otpService.verify(-1L, OtpPurpose.EMAIL_VERIFICATION, request.getOtp()));
            return;
        }

        if (user.isEmailVerified()) {
            // Idempotent: a double submission (or a retry after a lost response) is not an error.
            return;
        }

        requireVerified(otpService.verify(
                user.getId(), OtpPurpose.EMAIL_VERIFICATION, request.getOtp()));

        // Update only verification columns; don't overwrite a concurrent password/token update.
        userRepository.markEmailVerified(user.getId(), OffsetDateTime.now());
        auditService.emailVerified(user.getId(), user.getEmail(), ip);
    }

    /**
     * Issues a new verification code when the previous one expired or was lost.
     * The endpoint answers the same way for unknown, already-verified and cooling-down
     * addresses, so it cannot be used to discover which emails have accounts.
     */
    @Transactional
    public void resendVerification(ResendVerificationRequest request) {

        String email = normalise(request.getEmail());
        accountLock.acquire(email);
        if (userRepository.findByEmail(email).filter(User::isEmailVerified).isPresent()) return;

        // First, check for pending registration (new flow)
        Optional<PendingRegistration> pendingOpt = pendingRegistrationRepository.findByEmailForUpdate(email);
        if (pendingOpt.isPresent()) {
            PendingRegistration pending = pendingOpt.get();

            // Check resend cooldown
            OffsetDateTime nextAllowed = pending.getLastSentAt()
                    .plusSeconds(otpService.getResendCooldownSeconds());
            if (nextAllowed.isAfter(OffsetDateTime.now())) {
                return;
            }

            // Check hourly limit
            if (!pending.canSend(OffsetDateTime.now(), otpService.getMaxPerHour())) {
                return;
            }

            // Generate new OTP
            String otpCode = generateOtpCode();
            String otpHash = otpHasher.hashForPendingRegistration(otpCode, otpHasher.getKey());

            pending.setOtpHash(otpHash);
            pending.setAttempts(0);
            pending.setConsumedAt(null);
            pending.setExpiresAt(OffsetDateTime.now().plusMinutes(otpService.getTtlMinutes()));
            pending.recordSend(OffsetDateTime.now());
            pendingRegistrationRepository.save(pending);

            // Send new OTP email
            EmailTemplates.EmailContent content =
                    emailTemplates.verificationOtp(otpCode, otpService.getTtlMinutes());
            sendPendingRegistrationEmail(email, content);

            return;
        }

        // Backwards compatibility: existing unverified user
        userRepository.findByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> otpService.issue(user, OtpPurpose.EMAIL_VERIFICATION)
                        .ifPresent(otp -> sendQuietly(user, EmailPurpose.VERIFICATION, otp)));
    }

    private void sendPendingRegistrationEmail(String email, EmailTemplates.EmailContent content) {
        try {
            emailSender.send(email, content.subject(), content.html());
        } catch (EmailDeliveryException exception) {
            log.warn("Verification email could not be delivered for pending registration: {}", email);
        }
    }

    // ---------------------------------------------------------------- login / logout

    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {

        String email = normalise(request.getEmail());
        String ip = clientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.getPassword(), timingEqualisationHash);
            auditService.loginAttempt(email, ip, ua, false, "INVALID_CREDENTIALS");
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            auditService.loginAttempt(email, ip, ua, false, "INVALID_CREDENTIALS");
            throw new InvalidCredentialsException("Invalid email or password");
        }

        /*
         * Checked only after the password is known to be correct, so the response cannot be
         * used to learn that an address is registered by someone who does not know the password.
         */
        if (!user.isEmailVerified()) {
            auditService.loginAttempt(email, ip, ua, false, "EMAIL_NOT_VERIFIED");
            throw new EmailNotVerifiedException(
                    "Email address is not verified. Enter the code sent to your inbox or request a new one.");
        }

        auditService.loginAttempt(email, ip, ua, true, null);

        return new AuthResponse(
                jwtService.generateToken(user),
                "Bearer",
                jwtService.getExpirationSeconds());
    }

    /**
     * Revokes the presented token. Other devices keep working; use a password change to
     * invalidate every session at once.
     */
    @Transactional
    public void logout(String bearerToken, HttpServletRequest httpRequest) {

        if (bearerToken == null || bearerToken.isBlank()) {
            return;
        }

        String ip = clientIp(httpRequest);

        jwtService.parse(bearerToken).ifPresent(claims -> {

            if (claims.jti() == null) {
                return;
            }

            revokedTokenRepository.revoke(
                    claims.jti(),
                    claims.expiresAt().atOffset(ZoneOffset.UTC));

            auditService.logout(claims.userId(), ip, claims.jti().toString());

            // Opportunistic cleanup; expired rows are harmless but pointless.
            revokedTokenRepository.deleteByExpiresAtBefore(OffsetDateTime.now());
        });
    }

    // ---------------------------------------------------------------- password reset

    /**
     * Step 1. Always returns normally, whether or not the address is registered.
     */
    public void requestPasswordReset(ForgotPasswordRequest request, HttpServletRequest httpRequest) {

        String email = normalise(request.getEmail());
        String ip = clientIp(httpRequest);

        userRepository.findByEmail(email)
                .ifPresent(user -> otpService.issue(user, OtpPurpose.PASSWORD_RESET)
                        .ifPresent(otp -> sendQuietly(user, EmailPurpose.PASSWORD_RESET, otp)));

        auditService.passwordResetRequested(email, ip);
    }

    /**
     * Step 2. Exchanges a valid reset code for a single-use reset ticket.
     */
    @Transactional(noRollbackFor = ClientErrorException.class)
    public ResetOtpResponse verifyResetOtp(VerifyResetOtpRequest request, HttpServletRequest httpRequest) {

        String email = normalise(request.getEmail());
        String ip = clientIp(httpRequest);
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            requireVerified(otpService.verify(-1L, OtpPurpose.PASSWORD_RESET, request.getOtp()));
            throw new IllegalStateException("Unreachable: verification of an unknown account");
        }

        requireVerified(otpService.verify(
                user.getId(), OtpPurpose.PASSWORD_RESET, request.getOtp()));

        // Only one ticket may exist; re-verifying replaces any previous authorization.
        userRepository.findByIdForUpdate(user.getId()).orElseThrow();
        passwordResetTokenRepository.deleteByUserId(user.getId());
        passwordResetTokenRepository.flush();

        String ticket = randomToken();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(resetTicketTtlMinutes);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUserId(user.getId());
        resetToken.setTokenHash(sha256(ticket));
        resetToken.setExpiresAt(expiresAt);
        passwordResetTokenRepository.save(resetToken);

        return new ResetOtpResponse(ticket, expiresAt);
    }

    /**
     * Step 3. Replaces the password hash on the existing account, consumes the ticket and
     * invalidates every JWT issued before the change.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request, HttpServletRequest httpRequest) {

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw ClientErrorException.badRequest("Passwords do not match");
        }

        String ip = clientIp(httpRequest);

        Long resetUserId = passwordResetTokenRepository.findUserIdByTokenHash(sha256(request.getResetToken()))
                .orElseThrow(() -> ClientErrorException.badRequest("Invalid or expired reset authorization. Start the reset again."));
        User user = userRepository.findByIdForUpdate(resetUserId)
                .orElseThrow(() -> ClientErrorException.badRequest("Invalid or expired reset authorization. Start the reset again."));
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHashForUpdate(sha256(request.getResetToken()))
                .filter(token -> token.getUsedAt() == null
                        && token.getExpiresAt().isAfter(OffsetDateTime.now()))
                .orElseThrow(() -> ClientErrorException.badRequest(
                        "Invalid or expired reset authorization. Start the reset again."));

        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        // Kills access for anyone still holding a token minted with the old password.
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        resetToken.setUsedAt(OffsetDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        otpService.consumeActive(user.getId(), OtpPurpose.PASSWORD_RESET);

        auditService.passwordResetCompleted(user.getId(), ip);
        auditService.passwordChanged(user.getId(), ip, "reset");

        // A completed reset must not be undone by a mail outage.
        try {
            EmailTemplates.EmailContent content =
                    emailTemplates.passwordChanged(OffsetDateTime.now());
            emailSender.send(user.getEmail(), content.subject(), content.html());
        } catch (EmailDeliveryException exception) {
            log.warn("Password-change notification could not be delivered for userId={}",
                    user.getId());
        }
    }

    // ---------------------------------------------------------------- helpers

    private enum EmailPurpose {
        VERIFICATION,
        PASSWORD_RESET
    }

    private void sendQuietly(User user, EmailPurpose purpose, OtpService.IssuedOtp otp) {

        try {
            EmailTemplates.EmailContent content = purpose == EmailPurpose.VERIFICATION
                    ? emailTemplates.verificationOtp(otp.code(), otpService.getTtlMinutes())
                    : emailTemplates.passwordResetOtp(otp.code(), otpService.getTtlMinutes());

            emailSender.send(user.getEmail(), content.subject(), content.html());

        } catch (EmailDeliveryException exception) {
            /*
             * Never propagate: a 503 here would tell an unauthenticated caller that the address
             * exists. The code stays valid, so the user can retry once mail is available.
             */
            log.warn("{} email could not be delivered for userId={}",
                    purpose, user.getId());
        }
    }

    private static void requireVerified(OtpService.VerificationResult result) {

        switch (result.status()) {

            case VERIFIED -> { }

            case ATTEMPTS_EXCEEDED -> throw new ClientErrorException(
                    "OTP_ATTEMPTS_EXCEEDED",
                    "Too many incorrect codes. Request a new code.");

            case EXPIRED -> throw new ClientErrorException(
                    "OTP_EXPIRED",
                    "The code has expired. Request a new code.");

            case INVALID -> throw new ClientErrorException(
                    "OTP_INVALID",
                    "Invalid code. " + result.attemptsRemaining() + " attempt(s) remaining.");

            case NOT_FOUND -> throw new ClientErrorException(
                    "OTP_INVALID",
                    "Invalid or expired code. Request a new code.");
        }
    }

    private static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
