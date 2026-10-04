package com.dravya.setu.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENCY_KEY_ATTRIBUTE = "idempotencyKey";

    private static final String REQUEST_ID_PATTERN = "[A-Za-z0-9._-]{1,64}";
    private static final String IDEMPOTENCY_KEY_PATTERN = "[A-Za-z0-9._-]{1,64}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        // X-Request-Id: for tracing only (generate if missing)
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || !requestId.matches(REQUEST_ID_PATTERN)) {
            requestId = UUID.randomUUID().toString();
        }
        request.setAttribute(REQUEST_ID_HEADER, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        // Idempotency-Key: for idempotency only (client-controlled, optional)
        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey != null) {
            if (!idempotencyKey.matches(IDEMPOTENCY_KEY_PATTERN)) {
                writeErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                        "INVALID_REQUEST", "Invalid Idempotency-Key", requestId);
                return;
            }
            request.setAttribute(IDEMPOTENCY_KEY_ATTRIBUTE, idempotencyKey);
        }

        try {
            MDC.put("requestId", requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
        }
    }

    private void writeErrorResponse(HttpServletResponse response, int status,
            String code, String message, String requestId) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        ErrorResponse errorResponse = new ErrorResponse(code, message, requestId);
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
