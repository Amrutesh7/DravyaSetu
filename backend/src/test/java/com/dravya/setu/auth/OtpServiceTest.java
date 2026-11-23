package com.dravya.setu.auth;

import com.dravya.setu.users.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceTest {

    private static final long USER_ID = 7L;
    private static final String SECRET = "test-pepper-that-is-long-enough";

    @Mock private EmailOtpRepository repository;
    @Mock private com.dravya.setu.users.UserRepository users;

    private final OtpHasher hasher = new OtpHasher(SECRET);
    private OtpService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new OtpService(repository, users, hasher, 6, 5, 5, 60, 5);

        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        when(repository.save(any(EmailOtp.class))).thenAnswer(call -> call.getArgument(0));
        when(repository.countByUserIdAndPurposeAndCreatedAtAfter(anyLong(), any(), any()))
                .thenReturn(0L);
        when(repository.findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByLastSentAtDesc(
                anyLong(), any())).thenReturn(Optional.empty());
    }

    // ---------------------------------------------------------------- issuing

    @Test
    void issuesASixDigitCodeThatExpiresInFiveMinutesAndIsStoredOnlyAsADigest() {

        OtpService.IssuedOtp issued = service.issue(user, OtpPurpose.EMAIL_VERIFICATION)
                .orElseThrow();

        assertTrue(issued.code().matches("\\d{6}"), issued.code());
        assertTrue(issued.expiresAt().isAfter(OffsetDateTime.now().plusMinutes(4)));
        assertTrue(issued.expiresAt().isBefore(OffsetDateTime.now().plusMinutes(6)));

        ArgumentCaptor<EmailOtp> stored = ArgumentCaptor.forClass(EmailOtp.class);
        verify(repository).save(stored.capture());

        assertEquals(64, stored.getValue().getOtpHash().length());
        assertNotEquals(issued.code(), stored.getValue().getOtpHash());
        assertFalse(stored.getValue().getOtpHash().contains(issued.code()));
        assertEquals(OtpPurpose.EMAIL_VERIFICATION, stored.getValue().getPurpose());
        assertEquals(0, stored.getValue().getAttempts());
        assertNull(stored.getValue().getConsumedAt());

        // The digest is verifiable and bound to this user and purpose.
        assertTrue(hasher.matches(
                USER_ID, OtpPurpose.EMAIL_VERIFICATION,
                issued.code(), stored.getValue().getOtpHash()));
        assertFalse(hasher.matches(
                USER_ID, OtpPurpose.PASSWORD_RESET,
                issued.code(), stored.getValue().getOtpHash()));
    }

    @Test
    void issuesUnpredictableCodes() {

        java.util.Set<String> codes = new java.util.HashSet<>();

        for (int index = 0; index < 25; index++) {
            codes.add(service.issue(user, OtpPurpose.EMAIL_VERIFICATION).orElseThrow().code());
        }

        assertTrue(codes.size() > 20, "codes repeated too often: " + codes.size());
    }

    @Test
    void refusesToResendWhileTheCooldownIsRunning() {

        EmailOtp active = new EmailOtp();
        active.setUserId(USER_ID);
        active.setPurpose(OtpPurpose.EMAIL_VERIFICATION);
        active.setLastSentAt(OffsetDateTime.now().minusSeconds(5));

        when(repository.findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByLastSentAtDesc(
                USER_ID, OtpPurpose.EMAIL_VERIFICATION)).thenReturn(Optional.of(active));

        assertTrue(service.issue(user, OtpPurpose.EMAIL_VERIFICATION).isEmpty());
        verify(repository, never()).save(any(EmailOtp.class));
    }

    @Test
    void refusesToExceedTheHourlyCap() {

        when(repository.countByUserIdAndPurposeAndCreatedAtAfter(anyLong(), any(), any()))
                .thenReturn(5L);

        assertTrue(service.issue(user, OtpPurpose.PASSWORD_RESET).isEmpty());
    }

    @Test
    void retiresThePreviousActiveCodeBeforeIssuingANewOne() {

        EmailOtp active = new EmailOtp();
        active.setUserId(USER_ID);
        active.setPurpose(OtpPurpose.EMAIL_VERIFICATION);
        active.setLastSentAt(OffsetDateTime.now().minusMinutes(2));

        when(repository.findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByLastSentAtDesc(
                USER_ID, OtpPurpose.EMAIL_VERIFICATION)).thenReturn(Optional.of(active));

        assertTrue(service.issue(user, OtpPurpose.EMAIL_VERIFICATION).isPresent());
        assertNotNull(active.getConsumedAt());
    }

    // ---------------------------------------------------------------- verifying

    @Test
    void acceptsTheCodeOnceAndInvalidatesItImmediately() {

        OtpService.IssuedOtp issued = service.issue(user, OtpPurpose.EMAIL_VERIFICATION)
                .orElseThrow();
        EmailOtp active = captureActive(issued.code());

        when(repository.findActiveForUpdate(USER_ID, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(active));

        OtpService.VerificationResult result = service.verify(
                USER_ID, OtpPurpose.EMAIL_VERIFICATION, issued.code());

        assertEquals(OtpService.VerificationStatus.VERIFIED, result.status());
        assertNotNull(active.getConsumedAt());

        // Second submission of the same code can no longer find an active row.
        when(repository.findActiveForUpdate(USER_ID, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.empty());

        assertEquals(OtpService.VerificationStatus.NOT_FOUND,
                service.verify(USER_ID, OtpPurpose.EMAIL_VERIFICATION, issued.code()).status());
    }

    @Test
    void countsAWrongCodeAndReportsRemainingAttempts() {

        EmailOtp active = activeRow(service.issue(user, OtpPurpose.EMAIL_VERIFICATION)
                .orElseThrow().code());

        when(repository.findActiveForUpdate(USER_ID, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(active));

        OtpService.VerificationResult result =
                service.verify(USER_ID, OtpPurpose.EMAIL_VERIFICATION, "000000");

        assertEquals(OtpService.VerificationStatus.INVALID, result.status());
        assertEquals(4, result.attemptsRemaining());
        assertEquals(1, active.getAttempts());
        assertNull(active.getConsumedAt());
    }

    @Test
    void burnsTheCodeAfterFiveWrongAttempts() {

        EmailOtp active = activeRow(service.issue(user, OtpPurpose.EMAIL_VERIFICATION)
                .orElseThrow().code());

        when(repository.findActiveForUpdate(USER_ID, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(active));

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertEquals(OtpService.VerificationStatus.INVALID,
                    service.verify(USER_ID, OtpPurpose.EMAIL_VERIFICATION, "000000").status());
        }

        OtpService.VerificationResult fifth =
                service.verify(USER_ID, OtpPurpose.EMAIL_VERIFICATION, "000000");

        assertEquals(OtpService.VerificationStatus.ATTEMPTS_EXCEEDED, fifth.status());
        assertNotNull(active.getConsumedAt());

        // Even the correct code is now rejected.
        when(repository.findActiveForUpdate(USER_ID, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.empty());
        assertEquals(OtpService.VerificationStatus.NOT_FOUND,
                service.verify(USER_ID, OtpPurpose.EMAIL_VERIFICATION, "anything").status());
    }

    @Test
    void rejectsAnExpiredCodeAndInvalidatesIt() {

        EmailOtp active = activeRow(service.issue(user, OtpPurpose.PASSWORD_RESET)
                .orElseThrow().code());
        active.setExpiresAt(OffsetDateTime.now().minusSeconds(1));

        when(repository.findActiveForUpdate(USER_ID, OtpPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(active));

        OtpService.VerificationResult result = service.verify(
                USER_ID, OtpPurpose.PASSWORD_RESET, "123456");

        assertEquals(OtpService.VerificationStatus.EXPIRED, result.status());
        assertNotNull(active.getConsumedAt());
    }

    @Test
    void rejectsACodeFromTheOtherPurpose() {

        OtpService.IssuedOtp verification =
                service.issue(user, OtpPurpose.EMAIL_VERIFICATION).orElseThrow();

        EmailOtp active = activeRow(verification.code());
        active.setPurpose(OtpPurpose.PASSWORD_RESET);

        when(repository.findActiveForUpdate(USER_ID, OtpPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(active));

        assertEquals(OtpService.VerificationStatus.INVALID,
                service.verify(USER_ID, OtpPurpose.PASSWORD_RESET, verification.code()).status());
    }

    // ---------------------------------------------------------------- helpers

    private EmailOtp activeRow(String code) {
        EmailOtp otp = new EmailOtp();
        otp.setUserId(USER_ID);
        otp.setPurpose(OtpPurpose.EMAIL_VERIFICATION);
        otp.setOtpHash(hasher.hash(USER_ID, OtpPurpose.EMAIL_VERIFICATION, code));
        otp.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
        otp.setLastSentAt(OffsetDateTime.now());
        return otp;
    }

    private EmailOtp captureActive(String code) {
        return activeRow(code);
    }
}
