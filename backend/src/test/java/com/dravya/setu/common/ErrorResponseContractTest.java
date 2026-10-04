package com.dravya.setu.common;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class ErrorResponseContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void errorResponse_shouldMatchSharedErrorEnvelope() throws Exception {
        ErrorResponse response = new ErrorResponse(
                "AI_SERVICE_UNAVAILABLE",
                "AI service is temporarily unavailable",
                "REQ-123"
        );

        String json = objectMapper.writeValueAsString(response);

        JsonNode root = objectMapper.readTree(json);

        assertTrue(root.has("error"));
        assertEquals(1, root.size());

        JsonNode error = root.get("error");

        assertNotNull(error);
        assertEquals(3, error.size());

        assertEquals(
                "AI_SERVICE_UNAVAILABLE",
                error.get("code").asText()
        );

        assertEquals(
                "AI service is temporarily unavailable",
                error.get("message").asText()
        );

        assertEquals(
                "REQ-123",
                error.get("request_id").asText()
        );

        assertFalse(error.has("requestId"));
    }
}