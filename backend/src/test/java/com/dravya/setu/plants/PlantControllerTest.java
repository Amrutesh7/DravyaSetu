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

class PlantControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PlantService plantService;

    @Mock
    private PlantRepository plantRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        PlantController plantController =
                new PlantController(plantService, plantRepository);

        mockMvc = MockMvcBuilders
                .standaloneSetup(plantController)
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

        when(plantService.getPlantById(999L))
                .thenThrow(
                        new ResponseStatusException(
                                NOT_FOUND,
                                "Plant not found"
                        )
                );

        mockMvc.perform(
                        get("/plants/999")
                )
                .andExpect(status().isNotFound());

        verify(plantService).getPlantById(999L);
    }
}