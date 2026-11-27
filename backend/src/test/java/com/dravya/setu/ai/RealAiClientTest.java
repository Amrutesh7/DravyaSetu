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

    @Test void sendsModalImagesFieldWithOrderedFilesTypesAndRequestId() {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> requestIdHeader = new AtomicReference<>();
        server.createContext("/ai/analyze", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            requestIdHeader.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            writeResponse(exchange);
        });
        var jpeg = new MockMultipartFile("image", "leaf.jpg", "image/jpeg", new byte[]{1, 2, 3});
        var png = new MockMultipartFile("image", "flower.png", "image/png", new byte[]{4, 5, 6});
        multipartClient.identify(new AiRequest(List.of(jpeg, png), List.of(), "modal-request", "LEAF"));

        String sent = body.get();
        assertEquals(2, java.util.regex.Pattern.compile("name=\"images\"; filename=").matcher(sent).results().count());
        assertTrue(sent.indexOf("filename=\"leaf.jpg\"") < sent.indexOf("filename=\"flower.png\""));
        assertTrue(sent.contains("Content-Type: image/jpeg"));
        assertTrue(sent.contains("Content-Type: image/png"));
        assertTrue(sent.contains("name=\"request_id\""));
        assertTrue(sent.contains("modal-request"));
        assertEquals("modal-request", requestIdHeader.get());
        org.junit.jupiter.api.Assertions.assertFalse(sent.contains("name=\"image\""));
        org.junit.jupiter.api.Assertions.assertFalse(sent.contains("name=\"plant_part\""));
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
