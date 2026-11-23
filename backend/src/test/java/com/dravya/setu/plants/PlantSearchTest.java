package com.dravya.setu.plants;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlantSearchTest {
    private Plant plant(String code, String name) {
        Plant plant = new Plant(); plant.setPlantCode(code);
        plant.setPlantIdentifier(name); plant.setCommonName(name);
        plant.setScientificName("Botanicus unrelated");
        return plant;
    }

    @Test
    void handlesDeletionInsertionSubstitutionAndAdjacentTransposition() {
        Plant tulasi = plant("PLANT_033", "Tulasi");
        for (String query : List.of("tulsi", "tulaasi", "tulasi", "tulaxi", "tualsi", "TULSI")) {
            assertEquals(List.of(tulasi), PlantSearch.similarPlants(List.of(tulasi), query), query);
        }
    }

    @Test
    void comparesRegionalNamesAndScientificWordGroups() {
        Plant tulasi = plant("PLANT_033", "Holy Basil");
        tulasi.setScientificName("Ocimum sanctum L.");
        tulasi.setAyurvedicRegionalNames(new String[]{"Tulasi", "Surasa"});
        for (String query : List.of("tulsi", "holy basli", "ocimum sanctm", "suraxa")) {
            assertEquals(List.of(tulasi), PlantSearch.similarPlants(List.of(tulasi), query), query);
        }
    }

    @Test
    void ranksClosestNamesAndCapsResultsDeterministically() {
        List<Plant> plants = new ArrayList<>();
        for (int i = 0; i < 7; i++) plants.add(plant("TEST_" + i, "tuls" + (char) ('a' + i)));
        Plant closest = plant("PLANT_033", "Tulasi"); plants.add(closest);
        List<Plant> result = PlantSearch.similarPlants(plants, "tulsi");
        assertEquals(5, result.size());
        assertSame(closest, result.getFirst());
        assertEquals(List.of("PLANT_033", "TEST_0", "TEST_1", "TEST_2", "TEST_3"),
                result.stream().map(Plant::getPlantCode).toList());
    }

    @Test
    void avoidsShortQueriesUnknownIdsAndUnrelatedNames() {
        Plant tulasi = plant("PLANT_033", "Tulasi");
        for (String query : List.of("tu", "tli", "%", "_", "PLANT_%", "PLANT_099", "xylophone")) {
            assertTrue(PlantSearch.similarPlants(List.of(tulasi), query).isEmpty(), query);
        }
    }

    @Test
    void commonNamesWinTiesWithBotanicalAuthorNames() {
        Plant neem = plant("PLANT_025", "Neem");
        Plant insulin = plant("PLANT_018", "Insulin Plant");
        insulin.setScientificName("Chamaecostus cuspidatus (Nees & Mart.)");
        assertSame(neem, PlantSearch.similarPlants(List.of(insulin, neem), "neen").getFirst());
    }
}
