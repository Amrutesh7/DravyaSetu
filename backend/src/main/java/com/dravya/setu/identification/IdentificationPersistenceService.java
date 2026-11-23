package com.dravya.setu.identification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class IdentificationPersistenceService {

    private final IdentificationRepository identificationRepository;
    private final IdentificationImageRepository identificationImageRepository;
    private final IdentificationHeatmapRepository identificationHeatmapRepository;

    public IdentificationPersistenceService(
            IdentificationRepository identificationRepository,
            IdentificationImageRepository identificationImageRepository,
            IdentificationHeatmapRepository identificationHeatmapRepository) {
        this.identificationRepository = identificationRepository;
        this.identificationImageRepository = identificationImageRepository;
        this.identificationHeatmapRepository = identificationHeatmapRepository;
    }

    @Transactional
    public Identification save(
            Identification identification,
            List<IdentificationImage> images,
            List<IdentificationHeatmapData> heatmaps) {

        Identification saved = identificationRepository.save(identification);

        List<IdentificationImage> savedImages = new ArrayList<>();
        for (IdentificationImage image : images) {
            image.setIdentificationId(saved.getId());
            savedImages.add(identificationImageRepository.save(image));
        }

        for (IdentificationHeatmapData data : heatmaps) {
            if (data.imageIndex() < 0 || data.imageIndex() >= savedImages.size()) {
                throw new IllegalStateException(
                        "Heatmap references an unknown image index: " + data.imageIndex());
            }
            IdentificationHeatmap heatmap = new IdentificationHeatmap();
            heatmap.setIdentificationId(saved.getId());
            heatmap.setIdentificationImageId(savedImages.get(data.imageIndex()).getId());
            heatmap.setStorageKey(data.storageKey());
            heatmap.setSourceUrl(data.sourceUrl());
            heatmap.setContentType(data.contentType());
            heatmap.setSequenceNumber(data.sequenceNumber());
            identificationHeatmapRepository.save(heatmap);
        }

        return saved;
    }
}
