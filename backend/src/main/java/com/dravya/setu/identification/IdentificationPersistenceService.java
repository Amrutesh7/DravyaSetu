package com.dravya.setu.identification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentificationPersistenceService {

    private final IdentificationRepository identificationRepository;
    private final IdentificationImageRepository identificationImageRepository;

    public IdentificationPersistenceService(
            IdentificationRepository identificationRepository,
            IdentificationImageRepository identificationImageRepository) {
        this.identificationRepository = identificationRepository;
        this.identificationImageRepository = identificationImageRepository;
    }

    @Transactional
    public Identification save(
            Identification identification,
            IdentificationImage image) {

        Identification saved = identificationRepository.save(identification);
        image.setIdentificationId(saved.getId());
        identificationImageRepository.save(image);
        return saved;
    }
}
