package com.dravya.setu.identification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IdentificationImageRepository
        extends JpaRepository<IdentificationImage, Long> {

    List<IdentificationImage> findByIdentificationIdOrderBySequenceNumberAsc(
            Long identificationId
    );
}