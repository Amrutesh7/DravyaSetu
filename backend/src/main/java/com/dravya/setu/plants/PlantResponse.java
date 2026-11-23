package com.dravya.setu.plants;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/** Public plant identity; database identifiers and entity fields stay internal. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PlantResponse(String plantId, String plantIdentifier, String commonName,
                            List<String> ayurvedicRegionalNames, String scientificName) {
    public static PlantResponse from(Plant plant) {
        return new PlantResponse(plant.getPlantCode(), plant.getPlantIdentifier(), plant.getCommonName(),
                List.of(plant.getAyurvedicRegionalNames()), plant.getScientificName());
    }
}
