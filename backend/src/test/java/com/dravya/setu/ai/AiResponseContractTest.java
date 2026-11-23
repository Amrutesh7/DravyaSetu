package com.dravya.setu.ai;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResponseContractTest {

    @Test
    void deserializesDeployedBase64HeatmapContract() {
        String json = """
                {
                  "request_id": "ai-request-1",
                  "model_version": "efficientnetv2-s-dravyasetu-v2",
                  "image_quality": {"acceptable": true, "issues": []},
                  "identification": {"status": "KNOWN", "plant_id": "PLANT_001", "confidence": 0.94},
                  "alternatives": [],
                  "similar_species": [],
                  "explanation": {
                    "method": "GRAD_CAM",
                    "heatmaps": [
                      {"image_index": 1, "image_data": "aGVsbG8=", "media_type": "image/jpeg"}
                    ]
                  },
                  "multi_image": {"used": true, "image_count": 1},
                  "warnings": []
                }
                """;

        AiResponse response = new ObjectMapper().readValue(json, AiResponse.class);

        assertEquals("ai-request-1", response.getRequestId());
        assertEquals("KNOWN", response.getIdentification().getStatus());
        assertEquals(1, response.getExplanation().getHeatmaps().size());
        AiResponse.Heatmap heatmap = response.getExplanation().getHeatmaps().get(0);
        assertEquals(1, heatmap.getImageIndex());
        assertEquals("aGVsbG8=", heatmap.getImageData());
        assertEquals("image/jpeg", heatmap.getMediaType());
        assertTrue(response.getMultiImage().isUsed());
    }
}
