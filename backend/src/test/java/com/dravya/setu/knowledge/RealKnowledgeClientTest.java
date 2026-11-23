package com.dravya.setu.knowledge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealKnowledgeClientTest {

    private HttpServer server;
    private RealKnowledgeClient knowledgeClient;

    @BeforeEach
    void setUp() throws IOException {

        server = HttpServer.create(
                new InetSocketAddress("localhost", 0),
                0
        );

        server.start();

        int port = server.getAddress().getPort();

        knowledgeClient = new RealKnowledgeClient(
                "http://localhost:" + port,
                "/knowledge/chat",
                2000,
                2000
        );
    }

    @AfterEach
    void tearDown() {

        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void ask_shouldSendTheDeployedRequestContract()
            throws Exception {

        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> requestIdHeader = new AtomicReference<>();

        server.createContext(
                "/knowledge/chat",
                exchange -> {
                    body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    requestIdHeader.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
                    sendResponse(exchange, 200, canonicalResponse());
                }
        );

        KnowledgeRequest request = new KnowledgeRequest(
                "REQ_001",
                "SESSION_001",
                "What are the traditional uses of Tulasi?",
                "en",
                "kn",
                "PLANT_033",
                false
        );

        knowledgeClient.ask(request);

        String sent = body.get();

        assertNotNull(sent);
        assertTrue(sent.contains("\"request_id\":\"REQ_001\""), sent);
        assertTrue(sent.contains("\"session_id\":\"SESSION_001\""), sent);
        assertTrue(sent.contains("\"input_type\":\"text\""), sent);
        assertTrue(sent.contains("\"input_language\":\"en\""), sent);
        assertTrue(sent.contains("\"response_language\":\"kn\""), sent);
        assertTrue(sent.contains("\"plant_id\":\"PLANT_033\""), sent);
        assertTrue(sent.contains("\"generate_audio\":false"), sent);
        assertFalse(sent.contains("conversation_context"), sent);
        assertFalse(sent.contains("\"language\":"), sent);

        assertEquals("REQ_001", requestIdHeader.get());
    }

    @Test
    void ask_shouldDeserializeTheDeployedResponseContract()
            throws Exception {

        server.createContext(
                "/knowledge/chat",
                exchange -> sendResponse(exchange, 200, canonicalResponse())
        );

        KnowledgeResponse response = knowledgeClient.ask(new KnowledgeRequest(
                "REQ_001", "SESSION_001", "What are the traditional uses of Tulasi?",
                "en", null));

        assertNotNull(response);

        assertEquals("REQ_001", response.getRequestId());
        assertEquals("SESSION_001", response.getSessionId());
        assertEquals("en", response.getDetectedLanguage());
        assertNull(response.getTranscript());
        assertEquals("Tulasi is traditionally used for various purposes.", response.getAnswer());
        assertEquals("en", response.getLanguage());
        assertNotNull(response.getSources());
        assertEquals(1, response.getSources().size());
        assertEquals("SRC_001", response.getSources().get(0).getSourceId());
        assertEquals("Tulasi", response.getSources().get(0).getTitle());
        assertEquals("Tulasi.pdf", response.getSources().get(0).getReference());
        assertNull(response.getVoiceOutput());
        assertNotNull(response.getWarnings());
        assertTrue(response.getWarnings().isEmpty());
    }

    @Test
    void ask_shouldThrowKnowledgeServiceException_whenServerReturns500()
            throws Exception {

        server.createContext(
                "/knowledge/chat",
                exchange ->
                        sendResponse(
                                exchange,
                                500,
                                """
                                {
                                  "detail": "internal server error"
                                }
                                """
                        )
        );

        assertThrows(
                KnowledgeServiceException.class,
                () -> knowledgeClient.ask(new KnowledgeRequest(
                        "REQ_001", "SESSION_001", "What is this plant used for?",
                        "en", "PLANT_001"))
        );
    }

    @Test
    void ask_shouldThrowKnowledgeServiceException_whenResponseBodyIsEmpty()
            throws Exception {

        server.createContext(
                "/knowledge/chat",
                exchange -> sendResponse(exchange, 200, "")
        );

        assertThrows(
                KnowledgeServiceException.class,
                () -> knowledgeClient.ask(new KnowledgeRequest(
                        "REQ_001", "SESSION_001", "What is this plant used for?",
                        "en", "PLANT_001"))
        );
    }

    private String canonicalResponse() {
        return """
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
    }

    private void sendResponse(
            HttpExchange exchange,
            int statusCode,
            String responseBody)
            throws IOException {

        byte[] bytes =
                responseBody.getBytes(
                        StandardCharsets.UTF_8
                );

        exchange.getResponseHeaders()
                .set(
                        "Content-Type",
                        "application/json"
                );

        exchange.sendResponseHeaders(
                statusCode,
                bytes.length
        );

        try (OutputStream outputStream =
                     exchange.getResponseBody()) {

            outputStream.write(bytes);
        }
    }
}
