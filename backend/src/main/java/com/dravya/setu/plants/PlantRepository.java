package com.dravya.setu.plants;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import java.util.List;

public interface PlantRepository extends JpaRepository<Plant, Long> {

    Optional<Plant> findByPlantCode(String plantCode);

    List<Plant> findByCommonNameContainingIgnoreCase(String commonName);
}