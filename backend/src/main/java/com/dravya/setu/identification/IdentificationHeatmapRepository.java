package com.dravya.setu.identification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IdentificationHeatmapRepository
        extends JpaRepository<IdentificationHeatmap, Long> {

    @org.springframework.data.jpa.repository.Query("""
            select h from IdentificationHeatmap h, IdentificationImage i
            where h.identificationId = :identificationId and h.identificationImageId = i.id
            order by i.sequenceNumber, h.sequenceNumber, h.id
            """)
    List<IdentificationHeatmap> findByIdentificationIdOrderBySequenceNumberAsc(
            @org.springframework.data.repository.query.Param("identificationId") Long identificationId);
}
