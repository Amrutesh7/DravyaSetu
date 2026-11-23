package com.dravya.setu.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {

    Optional<PendingRegistration> findByEmail(String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PendingRegistration p where p.email = :email")
    Optional<PendingRegistration> findByEmailForUpdate(@Param("email") String email);

    boolean existsByEmail(String email);

    @Modifying
    @Transactional
    @Query("DELETE FROM PendingRegistration p WHERE p.expiresAt < :now")
    int deleteExpired(@Param("now") OffsetDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE PendingRegistration p SET p.consumedAt = :now WHERE p.email = :email AND p.consumedAt IS NULL")
    int markConsumed(@Param("email") String email, @Param("now") OffsetDateTime now);

    @Query("SELECT COUNT(p) FROM PendingRegistration p WHERE p.createdAt > :since")
    long countByCreatedAtAfter(@Param("since") OffsetDateTime since);
}
