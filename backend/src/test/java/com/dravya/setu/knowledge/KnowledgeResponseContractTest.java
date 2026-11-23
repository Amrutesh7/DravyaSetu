package com.dravya.setu.knowledge;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeResponseContractTest {

    private final JsonMapper objectMapper =
            JsonMapper.builder().build();

    @Test
    void shouldDeserializeTheDeployedKnowledgeResponse() {

        String json = """
                {
                  "request_id": "REQ_001",
                  "session_id": "SESSION_001",
                  "detected_language": "en",
                  "transcript": null,
                  "answer": "Tulasi is traditionally used for various purposes.",
                  "language": "en",
                  "sources": [
                    {
                      "source_id": "SRC_001",
                      "title": "Tulasi",
                      "reference": "Tulasi.pdf"
                    }
                  ],
                  "voice_output": null,
                  "warnings": []
                }
                """;

        KnowledgeResponse response =
                objectMapper.readValue(
                        json,
                        KnowledgeResponse.class
                );

        assertEquals("REQ_001", response.getRequestId());
        assertEquals("SESSION_001", response.getSessionId());
        assertEquals("en", response.getDetectedLanguage());
        assertNull(response.getTranscript());
        assertEquals("Tulasi is traditionally used for various purposes.", response.getAnswer());
        assertEquals("en", response.getLanguage());

        assertNotNull(response.getSources());
        assertEquals(1, response.getSources().size());
        assertEquals("SRC_001", response.getSources().get(0).getSourceId());

        assertNull(response.getVoiceOutput());
        assertTrue(response.getWarnings().isEmpty());
    }

    @Test
    void shouldDeserializeVoiceOutputAndIgnoreUnknownFields() {

        String json = """
                {
                  "request_id": "REQ_002",
                  "session_id": "SESSION_002",
                  "detected_language": "hi",
                  "transcript": " Tulasi ke fayde kya hain? ",
                  "answer": "Tulasi ke kai parmparkik upyog hain.",
                  "language": "hi",
                  "sources": [],
                  "voice_output": {
                    "available": true,
                    "audio_url": "https://example.com/answer.mp3"
                  },
                  "warnings": ["Answer is a translation"],
                  "future_field": "ignored"
                }
                """;

        KnowledgeResponse response =
                objectMapper.readValue(
                        json,
                        KnowledgeResponse.class
                );

        assertEquals("hi", response.getDetectedLanguage());
        assertEquals(" Tulasi ke fayde kya hain? ", response.getTranscript());
        assertNotNull(response.getVoiceOutput());
        assertEquals(true, response.getVoiceOutput().get("available"));
        assertEquals(1, response.getWarnings().size());
    }
}
