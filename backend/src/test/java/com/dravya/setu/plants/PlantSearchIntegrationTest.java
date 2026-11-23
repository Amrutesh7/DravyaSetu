package com.dravya.setu.plants;

import com.dravya.setu.TestFlywayConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestFlywayConfig.class)
@Transactional
class PlantSearchIntegrationTest {
    @Autowired PlantService service;
    @Autowired PlantRepository plants;
    @Autowired MockMvc http;

    @Test
    void findsCanonicalPlantsForCommonTypos() {
        for (var entry : Map.of("tulsi", "PLANT_033", "tualsi", "PLANT_033",
                "neen", "PLANT_025", "ashwgandha", "PLANT_006",
                "holy basli", "PLANT_033", "ocimum sanctm", "PLANT_033").entrySet()) {
            var results = service.searchPlants(entry.getKey());
            assertFalse(results.isEmpty(), entry.getKey());
            assertEquals(entry.getValue(), results.getFirst().getPlantCode(), entry.getKey());
        }
    }

    @Test
    void searchesAllIdentityFieldsAndNormalizesCaseAndWhitespace() {
        for (String query : List.of("PLANT_033", "Tulasi", "Holy Basil", "Surasa", "Ocimum", "  hOlY   bAsIl  ")) {
            assertTrue(service.searchPlants(query).stream().anyMatch(p -> p.getPlantCode().equals("PLANT_033")), query);
        }
    }

    @Test
    void ranksExactNamesBeforePrefixesAndSubstringsWithoutDuplicatePlants() {
        addPlant("SEARCH_PREFIX", "Tulasi extract");
        addPlant("SEARCH_SUBSTRING", "Garden Tulasi");
        assertEquals(List.of("PLANT_033", "SEARCH_PREFIX", "SEARCH_SUBSTRING"),
                service.searchPlants("tulasi").stream().map(Plant::getPlantCode).toList());
    }

    @Test
    void treatsSqlWildcardsLiterallyAndRejectsUnrelatedAndShortTypos() {
        for (String query : List.of("%", "PLANT_%", "PLANT_099", "xylophone", "tli")) {
            assertTrue(service.searchPlants(query).isEmpty(), query);
        }
        assertEquals(34, service.searchPlants(" ").size());
    }

    @Test
    void typoSearchKeepsFrontendContractAndStillRequiresAuthentication() throws Exception {
        http.perform(get("/plants").param("search", "tulsi")).andExpect(status().isUnauthorized());
        http.perform(get("/plants").param("search", "tulsi").with(user("frontend-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].plant_id").value("PLANT_033"))
                .andExpect(jsonPath("$[0].common_name").value("Holy Basil"))
                .andExpect(jsonPath("$[0].ayurvedic_regional_names[0]").value("Tulasi"))
                .andExpect(jsonPath("$[0].id").doesNotExist());
    }

    private void addPlant(String code, String name) {
        Plant plant = new Plant(); plant.setPlantCode(code); plant.setPlantIdentifier(code);
        plant.setCommonName(name); plant.setScientificName("Searchus testus");
        plant.setAyurvedicRegionalNames(new String[0]); plants.saveAndFlush(plant);
    }
}
