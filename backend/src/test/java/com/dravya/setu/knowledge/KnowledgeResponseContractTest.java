package com.dravya.setu.knowledge;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeResponseContractTest {

    private final JsonMapper objectMapper =
            JsonMapper.builder().build();

    @Test
    void shouldDeserializeCanonicalKnowledgeResponse()
            throws Exception {

        String json = """
                {
                  "request_id": "knowledge-001",
                  "answer": "Neem is a medicinal plant.",
                  "language": "en",
                  "sources": [
                    {
                      "title": "Test Source",
                      "url": "https://example.com/source"
                    }
                  ],
                  "voice_output": {
                    "available": true
                  },
                  "warnings": [],
                  "errors": []
                }
                """;

        KnowledgeResponse response =
                objectMapper.readValue(
                        json,
                        KnowledgeResponse.class
                );

        assertEquals(
                "knowledge-001",
                response.getRequestId()
        );

        assertEquals(
                "Neem is a medicinal plant.",
                response.getAnswer()
        );

        assertEquals(
                "en",
                response.getLanguage()
        );

        assertNotNull(
                response.getSources()
        );

        assertEquals(
                1,
                response.getSources().size()
        );

        assertNotNull(
                response.getVoiceOutput()
        );

        assertTrue(
                response.getWarnings().isEmpty()
        );

        assertTrue(
                response.getErrors().isEmpty()
        );
    }
}