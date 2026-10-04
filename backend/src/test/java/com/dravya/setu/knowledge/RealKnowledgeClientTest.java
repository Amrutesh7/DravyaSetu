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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
                "/ask",
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
    void ask_shouldDeserializeCanonicalResponse()
            throws Exception {

        server.createContext(
                "/ask",
                exchange -> {

                    String responseJson = """
                            {
                              "request_id": "knowledge-test-001",
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

                    sendResponse(
                            exchange,
                            200,
                            responseJson
                    );
                }
        );

        KnowledgeRequest request =
                new KnowledgeRequest(
                        "PLANT_001",
                        "What is this plant used for?",
                        "en"
                );

        KnowledgeResponse response =
                knowledgeClient.ask(request);

        assertNotNull(response);

        assertEquals(
                "knowledge-test-001",
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

        assertEquals(
                true,
                response.getVoiceOutput()
                        .get("available")
        );

        assertNotNull(
                response.getWarnings()
        );

        assertEquals(
                0,
                response.getWarnings().size()
        );

        assertNotNull(
                response.getErrors()
        );

        assertEquals(
                0,
                response.getErrors().size()
        );
    }

    @Test
    void ask_shouldThrowKnowledgeServiceException_whenServerReturns500()
            throws Exception {

        server.createContext(
                "/ask",
                exchange ->
                        sendResponse(
                                exchange,
                                500,
                                """
                                {
                                  "error": "internal server error"
                                }
                                """
                        )
        );

        KnowledgeRequest request =
                new KnowledgeRequest(
                        "PLANT_001",
                        "What is this plant used for?",
                        "en"
                );

        assertThrows(
                KnowledgeServiceException.class,
                () -> knowledgeClient.ask(request)
        );
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