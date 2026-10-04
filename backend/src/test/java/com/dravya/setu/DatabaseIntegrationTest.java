package com.dravya.setu;

import com.dravya.setu.identification.Identification;
import com.dravya.setu.identification.IdentificationImage;
import com.dravya.setu.identification.IdentificationImageRepository;
import com.dravya.setu.identification.IdentificationRepository;
import com.dravya.setu.plants.Plant;
import com.dravya.setu.plants.PlantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestFlywayConfig.class)
class DatabaseIntegrationTest {

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private IdentificationRepository identificationRepository;

    @Autowired
    private IdentificationImageRepository identificationImageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void applicationContext_shouldStartWithPostgres() {
        assertNotNull(plantRepository);
        assertNotNull(identificationRepository);
        assertNotNull(identificationImageRepository);
        assertNotNull(jdbcTemplate);
    }

    @Test
    void flyway_shouldHaveAppliedMigrations() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " +
                "FROM flyway_schema_history " +
                "WHERE success = true",
                Integer.class
        );

        assertNotNull(migrationCount);
        assertEquals(
                19,
                migrationCount,
                "Expected V1 to V19 Flyway migrations to be applied"
        );
    }

    @Test
    void expectedTables_shouldExist() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                      'users',
                      'plants',
                      'identifications',
                      'identification_images'
                  )
                """,
                Integer.class
        );

        assertNotNull(tableCount);
        assertEquals(
                4,
                tableCount,
                "Expected all four application tables to exist"
        );
    }

    @Test
    void plantRepository_shouldPersistAndReadPlant() {
        String plantCode = "INTEGRATION_" + System.currentTimeMillis();

        Plant plant = new Plant();
        plant.setPlantCode(plantCode);
        plant.setPlantIdentifier("TEST-PLANT-IDENTIFIER");
        plant.setAyurvedicRegionalNames(new String[]{"Test Regional Name"});
        plant.setCommonName("Integration Test Plant");
        plant.setScientificName("Testus plantus");
        plant.setFamily("Testaceae");

        Plant savedPlant = plantRepository.saveAndFlush(plant);

        assertNotNull(savedPlant.getId());
        assertNotNull(savedPlant.getCreatedAt());
        assertNotNull(savedPlant.getUpdatedAt());

        Plant loadedPlant = plantRepository.findById(savedPlant.getId()).orElseThrow();

        assertEquals(plantCode, loadedPlant.getPlantCode());
        assertEquals("TEST-PLANT-IDENTIFIER", loadedPlant.getPlantIdentifier());
        assertArrayEquals(new String[]{"Test Regional Name"}, loadedPlant.getAyurvedicRegionalNames());
        assertEquals("Integration Test Plant", loadedPlant.getCommonName());
        assertEquals("Testus plantus", loadedPlant.getScientificName());
        assertEquals("Testaceae", loadedPlant.getFamily());

        plantRepository.delete(savedPlant);
    }

    @Test
    void identificationAndImage_shouldPersistWithForeignKeys() {
        String email = "integration_" + System.currentTimeMillis() + "@example.com";

        Long userId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, password_hash, role)
                VALUES (?, ?, 'USER')
                RETURNING id
                """,
                Long.class, email, "integration-test-password");

        assertNotNull(userId);

        Plant plant = new Plant();
        plant.setPlantCode("INTEGRATION_" + System.currentTimeMillis());
        plant.setPlantIdentifier("RELATIONSHIP-TEST-IDENTIFIER");
        plant.setAyurvedicRegionalNames(new String[]{"Relationship Test Name"});
        plant.setCommonName("Relationship Test Plant");
        Plant savedPlant = plantRepository.saveAndFlush(plant);
        assertNotNull(savedPlant.getId());

        Identification identification = new Identification();
        identification.setUserId(userId);
        identification.setPlantId(savedPlant.getId());
        identification.setStatus("KNOWN");
        identification.setConfidence(BigDecimal.valueOf(0.9200));
        identification.setModelVersion("integration-test-v1");
        Identification savedIdentification = identificationRepository.saveAndFlush(identification);
        assertNotNull(savedIdentification.getId());
        assertNotNull(savedIdentification.getCreatedAt());

        IdentificationImage image = new IdentificationImage();
        image.setIdentificationId(savedIdentification.getId());
        image.setImageReference("integration-test-image.jpg");
        image.setPlantPart("LEAF");
        image.setSequenceNumber(1);
        IdentificationImage savedImage = identificationImageRepository.saveAndFlush(image);
        assertNotNull(savedImage.getId());
        assertNotNull(savedImage.getCreatedAt());

        Identification loadedIdentification = identificationRepository.findById(savedIdentification.getId()).orElseThrow();
        IdentificationImage loadedImage = identificationImageRepository.findById(savedImage.getId()).orElseThrow();

        assertEquals(userId, loadedIdentification.getUserId());
        assertEquals(savedPlant.getId(), loadedIdentification.getPlantId());
        assertEquals("KNOWN", loadedIdentification.getStatus());
        assertEquals(0, loadedIdentification.getConfidence().compareTo(BigDecimal.valueOf(0.92)));
        assertEquals("integration-test-v1", loadedIdentification.getModelVersion());
        assertEquals(savedIdentification.getId(), loadedImage.getIdentificationId());
        assertEquals("integration-test-image.jpg", loadedImage.getImageReference());
        assertEquals("LEAF", loadedImage.getPlantPart());
        assertEquals(1, loadedImage.getSequenceNumber());

        Long joinedRowCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM identifications i
                JOIN users u ON u.id = i.user_id
                JOIN plants p ON p.id = i.plant_id
                JOIN identification_images ii ON ii.identification_id = i.id
                WHERE i.id = ? AND u.id = ? AND p.id = ? AND ii.id = ?
                """,
                Long.class, savedIdentification.getId(), userId, savedPlant.getId(), savedImage.getId());

        assertEquals(1L, joinedRowCount);

        identificationImageRepository.delete(savedImage);
        identificationRepository.delete(savedIdentification);
        plantRepository.delete(savedPlant);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void plantRepository_shouldQueryPostgres() {
        assertNotNull(plantRepository.findAll());
    }
}
