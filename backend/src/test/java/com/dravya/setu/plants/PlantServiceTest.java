package com.dravya.setu.plants;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlantServiceTest {

    @Mock
    private PlantRepository plantRepository;

    private PlantService plantService;

    @BeforeEach
    void setUp() {
        plantService = new PlantService(plantRepository);
    }

    @Test
    void searchPlants_shouldFindByScientificNameAndRegionalNames() {
        // Arrange: mock the repository to return a plant for each search term
        Plant plant = new Plant();
        plant.setPlantCode("SEARCH_TEST_001");
        plant.setPlantIdentifier("SearchTest");
        plant.setAyurvedicRegionalNames(new String[]{"TestRegional", "AnotherName"});
        plant.setCommonName("Common Name");
        plant.setScientificName("Scientificus testus");
        plant.setFamily("Testaceae");

        // Mock: search by scientific name (partial)
        when(plantRepository.searchByTerm("Scientificus"))
                .thenReturn(List.of(plant));

        // Mock: search by regional name
        when(plantRepository.searchByTerm("TestRegional"))
                .thenReturn(List.of(plant));

        // Mock: search by common name (partial)
        when(plantRepository.searchByTerm("Common"))
                .thenReturn(List.of(plant));

        // Act & Assert: search by scientific name (partial)
        List<Plant> byScientific = plantService.searchPlants("Scientificus");
        assertEquals(1, byScientific.size());
        assertEquals("SEARCH_TEST_001", byScientific.get(0).getPlantCode());

        // Act & Assert: search by regional name
        List<Plant> byRegional = plantService.searchPlants("TestRegional");
        assertEquals(1, byRegional.size());
        assertEquals("SEARCH_TEST_001", byRegional.get(0).getPlantCode());

        // Act & Assert: search by common name (partial)
        List<Plant> byCommon = plantService.searchPlants("Common");
        assertEquals(1, byCommon.size());
        assertEquals("SEARCH_TEST_001", byCommon.get(0).getPlantCode());

        // Verify all searchByTerm calls
        verify(plantRepository, times(3)).searchByTerm(anyString());
    }

    @Test
    void getAllPlants_shouldReturnAllPlants() {

        Plant plant1 = new Plant();
        plant1.setPlantCode("PLANT_001");
        plant1.setCommonName("Test Plant 1");

        Plant plant2 = new Plant();
        plant2.setPlantCode("PLANT_002");
        plant2.setCommonName("Test Plant 2");

        when(plantRepository.findAll())
                .thenReturn(List.of(plant1, plant2));

        List<Plant> result = plantService.getAllPlants();

        assertEquals(2, result.size());
        assertEquals("PLANT_001", result.get(0).getPlantCode());
        assertEquals("PLANT_002", result.get(1).getPlantCode());

        verify(plantRepository).findAll();
    }

    @Test
    void searchPlants_shouldSearchByCommonName() {

        Plant plant = new Plant();
        plant.setPlantCode("PLANT_001");
        plant.setCommonName("Tulsi");

        when(plantRepository
                .searchByTerm("tulsi"))
                .thenReturn(List.of(plant));

        List<Plant> result =
                plantService.searchPlants("tulsi");

        assertEquals(1, result.size());
        assertEquals("Tulsi", result.get(0).getCommonName());

        verify(plantRepository)
                .searchByTerm("tulsi");
    }

    @Test
    void searchPlants_shouldReturnAllPlants_whenSearchIsBlank() {

        Plant plant = new Plant();
        plant.setPlantCode("PLANT_001");
        plant.setCommonName("Tulsi");

        when(plantRepository.findAll())
                .thenReturn(List.of(plant));

        List<Plant> result =
                plantService.searchPlants("   ");

        assertEquals(1, result.size());
        assertEquals("PLANT_001", result.get(0).getPlantCode());

        verify(plantRepository).findAll();

        verify(plantRepository, never())
                .searchByTerm(anyString());
    }

    @Test
    void getPlantById_shouldReturnPlant_whenPlantExists() {

        Plant plant = new Plant();
        plant.setPlantCode("PLANT_001");
        plant.setCommonName("Tulsi");

        when(plantRepository.findById(1L))
                .thenReturn(Optional.of(plant));

        Plant result =
                plantService.getPlantById(1L);

        assertNotNull(result);
        assertEquals("PLANT_001", result.getPlantCode());
        assertEquals("Tulsi", result.getCommonName());

        verify(plantRepository).findById(1L);
    }

    @Test
    void getPlantById_shouldThrowException_whenPlantDoesNotExist() {

        when(plantRepository.findById(999L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> plantService.getPlantById(999L)
        );

        assertEquals(
                404,
                exception.getStatusCode().value()
        );

        assertEquals(
                "Plant not found with id: 999",
                exception.getReason()
        );

        verify(plantRepository).findById(999L);
    }
}