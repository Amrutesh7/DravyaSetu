package com.dravya.setu.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {

    /**
     * Serialises concurrent submissions: the row is locked for the duration of the
     * verification transaction so two requests cannot both spend an attempt or both succeed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select otp from EmailOtp otp
            where otp.userId = :userId
              and otp.purpose = :purpose
              and otp.consumedAt is null
            """)
    Optional<EmailOtp> findActiveForUpdate(
            @Param("userId") Long userId,
            @Param("purpose") OtpPurpose purpose);

    Optional<EmailOtp> findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByLastSentAtDesc(
            Long userId, OtpPurpose purpose);

    long countByUserIdAndPurposeAndCreatedAtAfter(
            Long userId, OtpPurpose purpose, OffsetDateTime after);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EmailOtp otp
            set otp.consumedAt = :now
            where otp.userId = :userId
              and otp.purpose = :purpose
              and otp.consumedAt is null
            """)
    int consumeActive(
            @Param("userId") Long userId,
            @Param("purpose") OtpPurpose purpose,
            @Param("now") OffsetDateTime now);
}
