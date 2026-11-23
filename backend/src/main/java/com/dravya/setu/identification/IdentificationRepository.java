package com.dravya.setu.identification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IdentificationRepository
        extends JpaRepository<Identification, Long> {

    Optional<Identification> findByIdAndUserId(Long id, Long userId);

    Optional<Identification> findByUserIdAndRequestId(Long userId, String requestId);

    Page<Identification> findByUserId(Long userId, Pageable pageable);
}
