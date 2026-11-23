package com.dravya.setu.chatbot;

import com.dravya.setu.knowledge.KnowledgeSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatResponseContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serialisesThePublicChatContract() {

        ChatResponse response = new ChatResponse(
                "REQ_001",
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                "en",
                null,
                "Tulasi is traditionally used for various purposes.",
                "en",
                List.of(new KnowledgeSource("SRC_001", "Tulasi", "Tulasi.pdf")),
                null,
                List.of()
        );

        JsonNode node = objectMapper.readTree(
                objectMapper.writeValueAsString(response)
        );

        assertEquals("REQ_001", node.get("request_id").asText());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", node.get("session_id").asText());
        assertEquals("en", node.get("detected_language").asText());
        assertTrue(node.get("transcript").isNull());
        assertEquals("Tulasi is traditionally used for various purposes.", node.get("answer").asText());
        assertEquals("en", node.get("language").asText());
        assertEquals("SRC_001", node.get("sources").get(0).get("source_id").asText());
        assertEquals("Tulasi", node.get("sources").get(0).get("title").asText());
        assertEquals("Tulasi.pdf", node.get("sources").get(0).get("reference").asText());
        assertTrue(node.get("voice_output").isNull());
        assertTrue(node.get("warnings").isArray());

        // The deployed knowledge contract has no errors field and no conversation_context.
        assertFalse(node.has("errors"));
        assertFalse(node.has("conversation_context"));
    }
}
