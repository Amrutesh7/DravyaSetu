package com.dravya.setu.identification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdentificationRepository
        extends JpaRepository<Identification, Long> {

    Optional<Identification> findByIdAndUserId(Long id, Long userId);
}