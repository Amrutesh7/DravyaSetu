package com.dravya.setu.plants;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController //controls HTTP requests and return response data
@RequestMapping("/plants") //sets base URL
public class PlantController {

    private final PlantService plantService;
    private final PlantRepository plantRepository;

    public PlantController(PlantService plantService, PlantRepository plantRepository) {
        this.plantService = plantService;
        this.plantRepository = plantRepository;
    }


    @GetMapping("/{id}")
    public Plant getPlantById(@PathVariable Long id) {
        return plantService.getPlantById(id);
    }

    @GetMapping("/code/{plantCode}")
    public Plant getPlantByCode(@PathVariable String plantCode) {
        return plantRepository.findByPlantCode(plantCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plant not found with code: " + plantCode));
    }

    @GetMapping
    public List<Plant> getPlants(
            @RequestParam(required = false) String search) {

        return plantService.searchPlants(search);
    }
}