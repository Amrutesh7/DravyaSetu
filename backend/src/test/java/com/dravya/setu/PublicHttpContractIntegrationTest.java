package com.dravya.setu;

import com.dravya.setu.security.JwtService;
import com.dravya.setu.users.User;
import com.dravya.setu.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP checks include the servlet filters, unlike standalone controller tests. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "production"})
@Import(TestFlywayConfig.class)
class PublicHttpContractIntegrationTest {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired com.dravya.setu.security.RevokedTokenRepository revokedTokens;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> send(String path, String method, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
                .header("X-Request-Id", "http-contract-test")
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Set<String> fields(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.properties().forEach(entry -> names.add(entry.getKey()));
        return names;
    }

    @Test
    void openApiUsesActualSnakeCaseContracts() throws Exception {
        var response = send("/v3/api-docs", "GET", null);
        assertEquals(200, response.statusCode());
        var schemas = mapper.readTree(response.body()).path("components").path("schemas");
        assertEquals(Set.of("plant_id", "plant_identifier", "common_name",
                "ayurvedic_regional_names", "scientific_name"), fields(schemas.path("PlantResponse").path("properties")));
        assertTrue(fields(schemas.path("ChatRequest").path("properties")).containsAll(
                Set.of("plant_id", "session_id", "generate_audio")));
        assertEquals(Set.of("reset_token", "password", "confirm_password"),
                fields(schemas.path("ResetPasswordRequest").path("properties")));
        assertTrue(fields(schemas.path("IdentificationResponse").path("properties")).containsAll(
                Set.of("request_id", "plant_id", "model_version", "multi_image")));
    }

    @Test
    void securityErrorsHaveTheRequestIdBeforeAuthentication() throws Exception {
        var response = send("/plants", "GET", null);
        assertEquals(401, response.statusCode());
        assertEquals("http-contract-test", response.headers().firstValue("X-Request-Id").orElseThrow());
        assertEquals("http-contract-test", mapper.readTree(response.body()).path("error").path("request_id").asText());
    }

    @Test
    void reactPreflightAllowsAuthorizationAndExposesRequestId() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/chat"))
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("http://localhost:5173", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertTrue(response.headers().firstValue("Access-Control-Expose-Headers").orElseThrow().contains("X-Request-Id"));
    }

    @Test
    void missingRoutesAndWrongMethodsKeepTheirHttpSemantics() throws Exception {
        User user = new User();
        user.setEmail("http-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("not-used-in-this-test");
        user.setRole("USER"); user.setEmailVerified(true); user.setEmailVerifiedAt(OffsetDateTime.now());
        String token = jwt.generateToken(users.saveAndFlush(user));
        var absent = send("/does-not-exist", "GET", token);
        assertEquals(404, absent.statusCode());
        assertEquals("NOT_FOUND", mapper.readTree(absent.body()).path("error").path("code").asText());
        var method = send("/plants", "DELETE", token);
        assertEquals(405, method.statusCode());
        assertEquals("INVALID_REQUEST", mapper.readTree(method.body()).path("error").path("code").asText());
    }

    @Test
    void logoutCommitsRevocationAndRemovesExpiredTokens() throws Exception {
        UUID expiredId = UUID.randomUUID();
        revokedTokens.revoke(expiredId, OffsetDateTime.now().minusMinutes(1));
        User user = new User();
        user.setEmail("logout-http-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("not-used-in-this-test"); user.setRole("USER");
        user.setEmailVerified(true); user.setEmailVerifiedAt(OffsetDateTime.now());
        String token = jwt.generateToken(users.saveAndFlush(user));
        assertEquals(204, send("/auth/logout", "POST", token).statusCode());
        assertEquals(401, send("/auth/me", "GET", token).statusCode());
        assertFalse(revokedTokens.existsById(expiredId));
    }
}
