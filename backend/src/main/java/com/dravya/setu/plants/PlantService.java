package com.dravya.setu.plants;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.text.Normalizer;

@Service
public class PlantService {

    private final PlantRepository plantRepository;

    public PlantService(PlantRepository plantRepository)
    {
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

    @Transactional(readOnly = true)
    public Plant getPlantByCode(String plantCode) {
        return plantRepository.findByPlantCode(plantCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Plant not found with code: " + plantCode));
    }

    @Transactional(readOnly = true)
    public List<Plant> searchPlants(String search) {
        if (search == null || search.isBlank()) {
            return plantRepository.findAll();
        }
        String term = Normalizer.normalize(search, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}]+", " ").strip();
        if (term.isBlank()) return plantRepository.findAll();
        if (term.length() > 100) {
            throw new IllegalArgumentException("search must be at most 100 characters");
        }
        List<Plant> directMatches = plantRepository.searchByTerm(term);
        if (!directMatches.isEmpty() || !PlantSearch.isFuzzyQuery(term)) {
            return directMatches;
        }
        // The current catalogue has 34 plants; load it only after a strict search misses.
        return PlantSearch.similarPlants(plantRepository.findAll(), term);
    }
}
