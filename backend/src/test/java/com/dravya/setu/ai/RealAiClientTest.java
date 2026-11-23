package com.dravya.setu.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealAiClientTest {
    private HttpServer server;
    private RealAiClient multipartClient;
    private RealAiClient urlClient;

    @BeforeEach void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0); server.start();
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        multipartClient = new RealAiClient(baseUrl, "/ai/analyze", 2000, 2000, "multipart");
        urlClient = new RealAiClient(baseUrl, "/ai/analyze", 2000, 2000, "url");
    }
    @AfterEach void tearDown() { server.stop(0); }

    @Test void sendsMultipartContractAndReadsCanonicalResponse() {
        server.createContext("/ai/analyze", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            assertTrue(exchange.getRequestHeaders().getFirst("Content-Type").startsWith("multipart/form-data"));
            writeResponse(exchange);
        });
        var file = new MockMultipartFile("image", "plant.jpg", "image/jpeg", new byte[] {1, 2, 3});
        AiResponse response = multipartClient.identify(new AiRequest(file, "req-1", "LEAF"));
        assertEquals("req-1", response.getRequestId());
        assertEquals("UNKNOWN", response.getIdentification().getStatus());

        // Deployed service returns base64 heatmaps, not heatmap URLs.
        assertEquals(1, response.getExplanation().getHeatmaps().size());
        AiResponse.Heatmap heatmap = response.getExplanation().getHeatmaps().get(0);
        assertEquals(1, heatmap.getImageIndex());
        assertEquals("aGVsbG8=", heatmap.getImageData());
        assertEquals("image/jpeg", heatmap.getMediaType());
    }

    @Test void sendsSignedImageUrlsAsJson() {
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/ai/analyze", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            assertTrue(exchange.getRequestHeaders().getFirst("Content-Type").startsWith("application/json"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeResponse(exchange);
        });

        AiResponse response = urlClient.identify(new AiRequest(
                List.of(),
                List.of("https://bucket.example/image-1.jpg?sig=abc"),
                "req-2",
                "LEAF"));

        assertEquals("req-1", response.getRequestId());
        assertTrue(body.get().contains("https://bucket.example/image-1.jpg?sig=abc"));
        assertTrue(body.get().contains("\"image\""));
    }

    private static void writeResponse(com.sun.net.httpserver.HttpExchange exchange)
            throws IOException {
        // Deployed schema: explanation.heatmaps[] with base64 image_data
        byte[] response = "{\"request_id\":\"req-1\",\"model_version\":\"v1\",\"image_quality\":{\"acceptable\":true,\"issues\":[]},\"identification\":{\"status\":\"UNKNOWN\",\"plant_id\":null,\"confidence\":0.2},\"alternatives\":[],\"similar_species\":[],\"explanation\":{\"method\":\"GRAD_CAM\",\"heatmaps\":[{\"image_index\":1,\"image_data\":\"aGVsbG8=\",\"media_type\":\"image/jpeg\"}]},\"multi_image\":{\"used\":false,\"image_count\":1},\"warnings\":[]}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
