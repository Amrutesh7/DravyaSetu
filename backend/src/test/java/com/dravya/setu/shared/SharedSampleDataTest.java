package com.dravya.setu.shared;

import com.dravya.setu.ai.AiResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SharedSampleDataTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Path sharedFile(String relativePath) {
        return Path.of("..", relativePath).normalize();
    }

    @Test
    void aiSample_shouldContainKnownAndUnknownCasesMatchingAiResponse() throws IOException {
        Path path = sharedFile("shared/sample-data/sample-ai-response.json");

        assertTrue(Files.exists(path), "AI sample file must exist: " + path);

        String json = Files.readString(path);
        JsonNode root = objectMapper.readTree(json);

        assertTrue(root.has("known_case"), "AI sample must contain known_case");
        assertTrue(root.has("unknown_case"), "AI sample must contain unknown_case");

        AiResponse known =
                objectMapper.treeToValue(root.get("known_case"), AiResponse.class);

        AiResponse unknown =
                objectMapper.treeToValue(root.get("unknown_case"), AiResponse.class);

        // KNOWN case
        assertEquals("AI-12345", known.getRequestId());
        assertEquals("plant_v1.0", known.getModelVersion());
        assertNotNull(known.getImageQuality());
        assertTrue(known.getImageQuality().isAcceptable());
        assertNotNull(known.getIdentification());

        assertEquals("KNOWN", known.getIdentification().getStatus());
        assertEquals("PLANT_001", known.getIdentification().getPlantId());
        assertEquals(0.947, known.getIdentification().getConfidence(), 0.000001);

        assertNotNull(known.getAlternatives());
        assertNotNull(known.getSimilarSpecies());
        assertNotNull(known.getExplanation());
        assertEquals("GRAD_CAM", known.getExplanation().getMethod());

        assertNotNull(known.getMultiImage());
        assertTrue(known.getMultiImage().isUsed());
        assertEquals(3, known.getMultiImage().getImageCount());

        assertNotNull(known.getWarnings());

        // UNKNOWN case
        assertEquals("AI-12346", unknown.getRequestId());
        assertEquals("plant_v1.0", unknown.getModelVersion());
        assertNotNull(unknown.getIdentification());

        assertEquals("UNKNOWN", unknown.getIdentification().getStatus());
        assertNull(unknown.getIdentification().getPlantId());
        assertEquals(0.21, unknown.getIdentification().getConfidence(), 0.000001);

        assertNotNull(unknown.getExplanation());
        assertEquals("GRAD_CAM", unknown.getExplanation().getMethod());

        assertNotNull(unknown.getMultiImage());
        assertFalse(unknown.getMultiImage().isUsed());
        assertEquals(1, unknown.getMultiImage().getImageCount());

        assertNotNull(unknown.getWarnings());
        assertFalse(unknown.getWarnings().isEmpty());
    }

    @Test
    void plantSample_shouldMatchCanonicalPlantIdentityShape() throws IOException {
        Path path = sharedFile("shared/sample-data/sample-plant.json");

        assertTrue(Files.exists(path), "Plant sample file must exist: " + path);

        String json = Files.readString(path);
        JsonNode plant = objectMapper.readTree(json);

        assertEquals("PLANT_001", plant.get("plant_id").asText());
        assertEquals("Aloevera", plant.get("plant_identifier").asText());
        assertEquals("Aloe Vera", plant.get("common_name").asText());
        assertEquals(
                "Aloe vera (L.) Burm.f.",
                plant.get("scientific_name").asText()
        );

        assertTrue(
                plant.get("ayurvedic_regional_names").isArray(),
                "ayurvedic_regional_names must be an array"
        );

        assertTrue(
                plant.get("ayurvedic_regional_names").size() > 0,
                "ayurvedic_regional_names must not be empty"
        );

        assertTrue(
                plant.get("plant_id").asText().matches("^PLANT_[0-9]{3}$"),
                "plant_id must match PLANT_###"
        );
    }

    @Test
    void knownAiPlantId_shouldMatchPlantSampleId() throws IOException {
        Path aiPath =
                sharedFile("shared/sample-data/sample-ai-response.json");

        Path plantPath =
                sharedFile("shared/sample-data/sample-plant.json");

        JsonNode aiRoot =
                objectMapper.readTree(Files.readString(aiPath));

        JsonNode plant =
                objectMapper.readTree(Files.readString(plantPath));

        String aiPlantId =
                aiRoot.get("known_case")
                        .get("identification")
                        .get("plant_id")
                        .asText();

        String samplePlantId =
                plant.get("plant_id").asText();

        assertEquals(
                samplePlantId,
                aiPlantId,
                "AI and plant sample must use the same canonical plant_id"
        );
    }
}