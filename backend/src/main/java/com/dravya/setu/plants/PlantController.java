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


    @GetMapping("/{plantId}")
    public PlantResponse getPlantById(@PathVariable String plantId) {
        return PlantResponse.from(plantService.getPlantByCode(plantId));
    }

    @GetMapping("/code/{plantCode}")
    public PlantResponse getPlantByCode(@PathVariable String plantCode) {
        return getPlantById(plantCode);
    }

    @GetMapping
    public List<PlantResponse> getPlants(
            @RequestParam(required = false) String search) {

        return plantService.searchPlants(search).stream().map(PlantResponse::from).toList();
    }
}
