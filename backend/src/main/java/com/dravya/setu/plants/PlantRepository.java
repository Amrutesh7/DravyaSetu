package com.dravya.setu.plants;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import java.util.List;

public interface PlantRepository extends JpaRepository<Plant, Long> {

    Optional<Plant> findByPlantCode(String plantCode);

    @Query(value = """
            SELECT p.* FROM plants p
            JOIN LATERAL (
                SELECT min(CASE
                    WHEN lower(name) = lower(:term) THEN 0
                    WHEN strpos(lower(name), lower(:term)) = 1 THEN 1
                    ELSE 2 END) AS priority
                FROM unnest(ARRAY[p.plant_code, p.plant_identifier, p.common_name,
                                  p.scientific_name] || p.ayurvedic_regional_names) AS names(name)
                WHERE strpos(lower(name), lower(:term)) > 0
            ) matched ON matched.priority IS NOT NULL
            ORDER BY matched.priority, lower(p.common_name), p.plant_code
            """, nativeQuery = true)
    List<Plant> searchByTerm(@Param("term") String term);
}
