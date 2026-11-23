package com.dravya.setu;

import com.dravya.setu.ai.AiRequest;
import com.dravya.setu.ai.MockAiClient;
import com.dravya.setu.knowledge.KnowledgeRequest;
import com.dravya.setu.knowledge.MockKnowledgeClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MockIntegrationClientTest {

    @Test
    void mockClientsUseTheSameCanonicalContractsAsRealClients() {
        var image = new MockMultipartFile("image", "aloe.jpg", "image/jpeg", new byte[] {1, 2});
        var ai = new MockAiClient().identify(new AiRequest(image, "request-123", "LEAF"));
        var knowledge = new MockKnowledgeClient().ask(new KnowledgeRequest(
                "request-123", "SESSION-123", "What is this plant?", "en", "PLANT_001"));

        assertEquals("request-123", ai.getRequestId());
        assertEquals("KNOWN", ai.getIdentification().getStatus());
        assertEquals("PLANT_001", ai.getIdentification().getPlantId());
        assertEquals("request-123", knowledge.getRequestId());
        assertEquals("SESSION-123", knowledge.getSessionId());
        assertNotNull(knowledge.getAnswer());
    }
}
