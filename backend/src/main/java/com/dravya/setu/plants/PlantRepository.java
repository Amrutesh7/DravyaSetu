package com.dravya.setu.plants;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import java.util.List;

public interface PlantRepository extends JpaRepository<Plant, Long> {

    Optional<Plant> findByPlantCode(String plantCode);

    @Query(value = "SELECT * FROM plants " +
            "WHERE common_name ILIKE %:term% " +
            "   OR scientific_name ILIKE %:term% " +
            "   OR EXISTS (SELECT 1 FROM unnest(ayurvedic_regional_names) AS regional(name) " +
            "              WHERE regional.name ILIKE %:term%)",
            nativeQuery = true)
    List<Plant> searchByTerm(@Param("term") String term);
}
