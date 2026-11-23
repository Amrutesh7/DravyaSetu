package com.dravya.setu.auth;

import com.dravya.setu.users.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Issues and verifies email OTPs.
 *
 * <p>Guarantees, in order of importance:</p>
 * <ul>
 *   <li>the plaintext code is never stored, only an HMAC digest bound to user and purpose;</li>
 *   <li>a code expires after {@code app.otp.ttl-minutes} (default 5);</li>
 *   <li>a wrong code is worth at most {@code app.otp.max-attempts} (default 5) tries;</li>
 *   <li>a code is single use, and only one code per user/purpose can be active;</li>
 *   <li>resends are throttled by a cooldown and an hourly cap.</li>
 * </ul>
 */
@Service
public class OtpService {

    private final EmailOtpRepository repository;
    private final com.dravya.setu.users.UserRepository users;
    private final OtpHasher hasher;
    private final SecureRandom random = new SecureRandom();

    private final int length;
    private final long ttlMinutes;
    private final int maxAttempts;
    private final long resendCooldownSeconds;
    private final int maxPerHour;

    public OtpService(
            EmailOtpRepository repository,
            com.dravya.setu.users.UserRepository users,
            OtpHasher hasher,
            @Value("${app.otp.length:6}") int length,
            @Value("${app.otp.ttl-minutes:5}") long ttlMinutes,
            @Value("${app.otp.max-attempts:5}") int maxAttempts,
            @Value("${app.otp.resend-cooldown-seconds:60}") long resendCooldownSeconds,
            @Value("${app.otp.max-per-hour:5}") int maxPerHour) {

        if (length < 4 || length > 10) {
            throw new IllegalStateException("app.otp.length must be between 4 and 10");
        }
        if (maxAttempts < 1 || maxAttempts > 20) {
            throw new IllegalStateException("app.otp.max-attempts must be between 1 and 20");
        }
        if (ttlMinutes < 1 || resendCooldownSeconds < 0 || maxPerHour < 1) {
            throw new IllegalStateException("Invalid OTP timing configuration");
        }

        this.repository = repository;
        this.users = users;
        this.hasher = hasher;
        this.length = length;
        this.ttlMinutes = ttlMinutes;
        this.maxAttempts = maxAttempts;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxPerHour = maxPerHour;
    }

    public long getTtlMinutes() {
        return ttlMinutes;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public int getLength() {
        return length;
    }

    public long getResendCooldownSeconds() {
        return resendCooldownSeconds;
    }

    public int getMaxPerHour() {
        return maxPerHour;
    }

    /** Outcome of a verification attempt. Failures are values, not exceptions. */
    public enum VerificationStatus {
        VERIFIED,
        INVALID,
        EXPIRED,
        ATTEMPTS_EXCEEDED,
        NOT_FOUND
    }

    public record VerificationResult(VerificationStatus status, int attemptsRemaining) { }

    public record IssuedOtp(String code, OffsetDateTime expiresAt) { }

    /**
     * Creates a new active code, replacing any previous one, unless a resend is still cooling
     * down or the hourly cap has been reached. Joins the caller's transaction so that a failed
     * registration (for example an undeliverable email) rolls the code back with the account.
     */
    @Transactional
    public Optional<IssuedOtp> issue(User user, OtpPurpose purpose) {

        users.findByIdForUpdate(user.getId()).orElseThrow(() ->
                new IllegalStateException("OTP account no longer exists"));

        OffsetDateTime now = OffsetDateTime.now();

        long sentLastHour = repository.countByUserIdAndPurposeAndCreatedAtAfter(
                user.getId(), purpose, now.minusHours(1));

        if (sentLastHour >= maxPerHour) {
            return Optional.empty();
        }

        EmailOtp active = repository
                .findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByLastSentAtDesc(
                        user.getId(), purpose)
                .orElse(null);

        if (active != null) {

            OffsetDateTime nextAllowed =
                    active.getLastSentAt().plusSeconds(resendCooldownSeconds);

            if (nextAllowed.isAfter(now)) {
                return Optional.empty();
            }

            // Exactly one code may be active: retire the previous one before inserting.
            active.setConsumedAt(now);
            repository.saveAndFlush(active);
        }

        String code = generateCode();
        OffsetDateTime expiresAt = now.plusMinutes(ttlMinutes);

        EmailOtp otp = new EmailOtp();
        otp.setUserId(user.getId());
        otp.setPurpose(purpose);
        otp.setOtpHash(hasher.hash(user.getId(), purpose, code));
        otp.setExpiresAt(expiresAt);
        otp.setLastSentAt(now);
        repository.save(otp);

        return Optional.of(new IssuedOtp(code, expiresAt));
    }

    /**
     * Verifies a submitted code.
     *
     * <p>Joins an account transaction and returns failures as values. Account endpoints use
     * noRollbackFor on controlled client errors so wrong attempts commit; a failed ticket or
     * account write still rolls successful consumption back. Lock order is always user, OTP.</p>
     */
    @Transactional
    public VerificationResult verify(Long userId, OtpPurpose purpose, String submittedCode) {

        if (users.findByIdForUpdate(userId).isEmpty()) {
            hasher.simulateCheck(submittedCode);
            return new VerificationResult(VerificationStatus.NOT_FOUND, 0);
        }

        EmailOtp active = repository.findActiveForUpdate(userId, purpose).orElse(null);
        OffsetDateTime now = OffsetDateTime.now();

        if (active == null) {
            // Spend the same time as a real comparison before reporting failure.
            hasher.simulateCheck(submittedCode);
            return new VerificationResult(VerificationStatus.NOT_FOUND, 0);
        }

        if (!active.getExpiresAt().isAfter(now)) {
            active.setConsumedAt(now);
            repository.save(active);
            return new VerificationResult(VerificationStatus.EXPIRED, 0);
        }

        if (active.getAttempts() >= maxAttempts) {
            active.setConsumedAt(now);
            repository.save(active);
            return new VerificationResult(VerificationStatus.ATTEMPTS_EXCEEDED, 0);
        }

        if (!hasher.matches(userId, purpose, submittedCode, active.getOtpHash())) {

            int attempts = active.getAttempts() + 1;
            active.setAttempts(attempts);

            if (attempts >= maxAttempts) {
                // Burn the code once the budget is spent; a new code must be requested.
                active.setConsumedAt(now);
                repository.save(active);
                return new VerificationResult(VerificationStatus.ATTEMPTS_EXCEEDED, 0);
            }

            repository.save(active);
            return new VerificationResult(
                    VerificationStatus.INVALID, maxAttempts - attempts);
        }

        active.setConsumedAt(now);
        repository.save(active);
        return new VerificationResult(VerificationStatus.VERIFIED, maxAttempts);
    }

    /** Invalidates every active code of a purpose, for example after a completed reset. */
    @Transactional
    public void consumeActive(Long userId, OtpPurpose purpose) {
        repository.consumeActive(userId, purpose, OffsetDateTime.now());
    }

    private String generateCode() {

        StringBuilder code = new StringBuilder(length);

        for (int index = 0; index < length; index++) {
            code.append(random.nextInt(10));
        }

        return code.toString();
    }
}
