package com.dravya.setu.plants;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class PlantService {

    private final PlantRepository plantRepository;

    public PlantService(PlantRepository plantRepository) {
        this.plantRepository = plantRepository;
    }

    @Transactional(readOnly = true)
    public List<Plant> getAllPlants() {
        return plantRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Plant getPlantById(Long id) {
        return plantRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Plant not found with id: " + id
                        )
                );
    }

    public List<Plant> searchPlants(String search) {

        if (search == null || search.isBlank()) {
            return plantRepository.findAll();
        }

        return plantRepository
                .findByCommonNameContainingIgnoreCase(search.trim());
    }
}