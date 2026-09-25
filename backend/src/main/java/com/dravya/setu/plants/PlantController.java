package com.dravya.setu.plants;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController //controls HTTP requests and return response data
@RequestMapping("/plants") //sets base URL
public class PlantController {

    private final PlantService plantService;

    public PlantController(PlantService plantService) {
        this.plantService = plantService;
    }

    @GetMapping
    public List<Plant> getAllPlants() {
        return plantService.getAllPlants();
    }

    @GetMapping("/{id}")
    public Plant getPlantById(@PathVariable Long id) {
        return plantService.getPlantById(id);
    }

    @GetMapping
    public List<Plant> getPlants(
            @RequestParam(required = false) String search) {

        return plantService.searchPlants(search);
    }
}