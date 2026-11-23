package com.dravya.setu.common;

import com.dravya.setu.ai.AiServiceException;
import com.dravya.setu.knowledge.KnowledgeServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerContractTest {

    @Test
    void responseStatusesAlwaysUseDeclaredPublicCodes() throws Exception {
        var mapper = new tools.jackson.databind.ObjectMapper();
        var declared = mapper.readTree(java.nio.file.Files.readString(
                java.nio.file.Path.of("../shared/constants/error-codes.json"))).get("public_backend");
        var allowed = new java.util.HashSet<String>();
        declared.forEach(code -> allowed.add(code.asText()));
        for (HttpStatus status : HttpStatus.values()) {
            if (!status.isError()) continue;
            var response = handler.handleResponseStatus(
                    new org.springframework.web.server.ResponseStatusException(status), requestWithId("req"));
            assertEquals(status.value(), response.getStatusCode().value());
            assertTrue(allowed.contains(response.getBody().getError().getCode()), status.toString());
            assertNotNull(response.getBody().getError().getMessage());
        }
        assertTrue(allowed.contains(handler.handleUploadTooLarge(
                new org.springframework.web.multipart.MaxUploadSizeExceededException(1), requestWithId("req"))
                .getBody().getError().getCode()));
        assertTrue(allowed.contains("INTERNAL_ERROR"));
    }

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    @Test
    void badRequest_shouldUseInvalidRequestCode() {
        MockHttpServletRequest request = requestWithId("REQ-400");

        var response = handler.handleBadRequest(
                new IllegalArgumentException("Invalid request"),
                request
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        assertEquals(
                "INVALID_REQUEST",
                response.getBody().getError().getCode()
        );

        assertEquals(
                "REQ-400",
                response.getBody().getError().getRequestId()
        );
    }

    @Test
    void accessDenied_shouldUseAuthForbiddenCode() {
        MockHttpServletRequest request = requestWithId("REQ-403");

        var response = handler.handleAccessDenied(
                new AccessDeniedException("Access denied"),
                request
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());

        assertEquals(
                "AUTH_FORBIDDEN",
                response.getBody().getError().getCode()
        );

        assertEquals(
                "REQ-403",
                response.getBody().getError().getRequestId()
        );
    }

    @Test
    void aiServiceError_shouldUseAiServiceUnavailableCode() {
        MockHttpServletRequest request = requestWithId("REQ-503-AI");

        var response = handler.handleAiServiceError(
                new AiServiceException("AI unavailable"),
                request
        );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                "AI_SERVICE_UNAVAILABLE",
                response.getBody().getError().getCode()
        );
    }

    @Test
    void knowledgeServiceError_shouldUseKnowledgeServiceUnavailableCode() {
        MockHttpServletRequest request = requestWithId("REQ-503-KNOWLEDGE");

        var response = handler.handleKnowledgeServiceError(
                new KnowledgeServiceException("Knowledge unavailable"),
                request
        );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                "KNOWLEDGE_SERVICE_UNAVAILABLE",
                response.getBody().getError().getCode()
        );
    }

    @Test
    void unexpectedError_shouldUseInternalErrorCode() {
        MockHttpServletRequest request = requestWithId("REQ-500");

        var response = handler.handleUnexpectedError(
                new RuntimeException("Unexpected"),
                request
        );

        assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                "INTERNAL_ERROR",
                response.getBody().getError().getCode()
        );

        assertEquals(
                "REQ-500",
                response.getBody().getError().getRequestId()
        );
    }

    private MockHttpServletRequest requestWithId(String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest();

        request.setAttribute(
                RequestIdFilter.REQUEST_ID_HEADER,
                requestId
        );

        return request;
    }
}
