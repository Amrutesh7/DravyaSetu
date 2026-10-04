package com.dravya.setu.ai;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RealAiClientTest {

    private HttpServer server;
    private RealAiClient aiClient;

    @BeforeEach
    void setUp() throws IOException {

        server = HttpServer.create(
                new InetSocketAddress("localhost", 0),
                0
        );

        server.start();

        int port = server.getAddress().getPort();

        aiClient = new RealAiClient(
                "http://localhost:" + port,
                "/identify",
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
    void identify_shouldDeserializeCanonicalResponse()
            throws Exception {

        server.createContext(
                "/identify",
                exchange -> {

                    String responseJson = """
                            {
                              "request_id": "ai-test-001",
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

                    sendResponse(
                            exchange,
                            200,
                            responseJson
                    );
                }
        );

        AiRequest request =
                new AiRequest(
                        "https://example.com/plant.jpg"
                );

        AiResponse response =
                aiClient.identify(request);

        assertNotNull(response);

        assertEquals(
                "ai-test-001",
                response.getRequestId()
        );

        assertEquals(
                "efficientnet-v2",
                response.getModelVersion()
        );

        assertNotNull(
                response.getImageQuality()
        );

        assertEquals(
                true,
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
    }

    @Test
    void identify_shouldThrowAiServiceException_whenServerReturns500()
            throws Exception {

        server.createContext(
                "/identify",
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

        AiRequest request =
                new AiRequest(
                        "https://example.com/plant.jpg"
                );

        assertThrows(
                AiServiceException.class,
                () -> aiClient.identify(request)
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