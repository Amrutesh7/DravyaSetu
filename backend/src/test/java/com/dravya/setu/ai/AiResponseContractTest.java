package com.dravya.setu.ai;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiResponseContractTest {

    private final JsonMapper objectMapper =
            JsonMapper.builder().build();

    @Test
    void shouldDeserializeCanonicalAiResponse() throws Exception {

        String json = """
                {
                  "request_id": "req-001",
                  "model_version": "efficientnet-v2",
                  "image_quality": {
                    "acceptable": true,
                    "issues": []
                  },
                  "identification": {
                    "status": "KNOWN",
                    "plant_id": "PLANT_001",
                    "confidence": 0.92
                  },
                  "alternatives": [
                    {
                      "plant_id": "PLANT_002",
                      "common_name": "Alternative Plant",
                      "scientific_name": "Testus alternativus",
                      "confidence": 0.05
                    }
                  ],
                  "similar_species": [
                    {
                      "plant_id": "PLANT_002",
                      "name": "Alternative Plant",
                      "similarity": 0.87
                    }
                  ],
                  "explanation": {
                    "method": "GRAD_CAM",
                    "heatmap_url": "https://example.com/heatmap.jpg"
                  },
                  "multi_image": {
                    "used": false,
                    "image_count": 1
                  },
                  "warnings": []
                }
                """;

        AiResponse response =
                objectMapper.readValue(
                        json,
                        AiResponse.class
                );

        assertEquals(
                "req-001",
                response.getRequestId()
        );

        assertEquals(
                "efficientnet-v2",
                response.getModelVersion()
        );

        assertNotNull(
                response.getImageQuality()
        );

        assertTrue(
                response.getImageQuality().isAcceptable()
        );

        assertNotNull(
                response.getIdentification()
        );

        assertEquals(
                "KNOWN",
                response.getIdentification().getStatus()
        );

        assertEquals(
                "PLANT_001",
                response.getIdentification().getPlantId()
        );

        assertEquals(
                0.92,
                response.getIdentification().getConfidence()
        );

        assertEquals(
                1,
                response.getAlternatives().size()
        );

        assertEquals(
                1,
                response.getSimilarSpecies().size()
        );

        assertEquals(
                "GRAD_CAM",
                response.getExplanation().getMethod()
        );

        assertEquals(
                1,
                response.getMultiImage().getImageCount()
        );

        assertTrue(
                response.getWarnings().isEmpty()
        );
    }
}