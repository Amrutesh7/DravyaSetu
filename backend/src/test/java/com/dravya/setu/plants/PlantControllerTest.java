package com.dravya.setu.plants;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.dravya.setu.common.GlobalExceptionHandler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class PlantControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PlantService plantService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        PlantController plantController =
                new PlantController(plantService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(plantController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getPlants_shouldReturn200() throws Exception {

        when(plantService.searchPlants(null))
                .thenReturn(List.of());

        mockMvc.perform(
                        get("/plants")
                )
                .andExpect(status().isOk());

        verify(plantService).searchPlants(null);
    }

    @Test
    void searchPlants_shouldReturn200() throws Exception {

        when(plantService.searchPlants("Neem"))
                .thenReturn(List.of());

        mockMvc.perform(
                        get("/plants")
                                .param("search", "Neem")
                )
                .andExpect(status().isOk());

        verify(plantService).searchPlants("Neem");
    }

    @Test
    void getPlantById_shouldReturn404WhenPlantDoesNotExist()
            throws Exception {

        when(plantService.getPlantByCode("PLANT_999"))
                .thenThrow(
                        new ResponseStatusException(
                                NOT_FOUND,
                                "Plant not found"
                        )
                );

        mockMvc.perform(
                        get("/plants/PLANT_999")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        verify(plantService).getPlantByCode("PLANT_999");
    }

    @Test
    void plantRoutesReturnExactlyTheSharedIdentityShape() throws Exception {
        Plant plant = new Plant();
        plant.setPlantCode("PLANT_001");
        plant.setPlantIdentifier("Aloevera");
        plant.setCommonName("Aloe Vera");
        plant.setAyurvedicRegionalNames(new String[]{"Kumari", "Ghritakumari"});
        plant.setScientificName("Aloe vera (L.) Burm.f.");
        plant.setDescription("Internal application content");
        when(plantService.getPlantByCode("PLANT_001")).thenReturn(plant);
        when(plantService.searchPlants(null)).thenReturn(List.of(plant));
        String expected = java.nio.file.Files.readString(
                java.nio.file.Path.of("../shared/sample-data/sample-plant.json"));
        for (String route : List.of("/plants/PLANT_001", "/plants/code/PLANT_001")) {
            mockMvc.perform(get(route)).andExpect(status().isOk())
                    .andExpect(content().json(expected, org.springframework.test.json.JsonCompareMode.STRICT));
        }
        mockMvc.perform(get("/plants")).andExpect(status().isOk())
                .andExpect(content().json("[" + expected + "]", org.springframework.test.json.JsonCompareMode.STRICT));
    }
}
