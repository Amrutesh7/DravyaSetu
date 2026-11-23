package com.dravya.setu.auth;

import com.dravya.setu.common.RequestIdFilter;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthAuditService {

    private static final Logger log = LoggerFactory.getLogger(AuthAuditService.class);
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    private final AuthAuditWriter writer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthAuditService(AuthAuditWriter writer) {
        this.writer = writer;
    }

    /**
     * Records an auth event. Runs in its own transaction so failures don't roll back
     * the main business operation (e.g., a failed login still logs the attempt).
     */
    public void record(String event, Long userId, String email, String ip, String userAgent,
                       boolean success, String reason, Map<String, Object> metadata) {

        String metadataJson = null;
        if (metadata != null && !metadata.isEmpty()) {
            try {
                metadataJson = objectMapper.writeValueAsString(metadata);
            } catch (Exception e) {
                log.warn("Failed to serialize audit metadata", e);
            }
        }

        AuthAuditLog entry = new AuthAuditLog(event, userId, email, ip, userAgent,
                success, reason, metadataJson);
        // A newly created user must commit before the independent audit writer references it.
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCompletion(int status) {
                            if (!success || status == STATUS_COMMITTED) persist(entry);
                        }
                    });
        } else {
            persist(entry);
        }

        // Structured JSON log for log aggregation (ELK, Datadog, CloudWatch, etc.)
        Map<String, Object> logMap = new LinkedHashMap<>();
        logMap.put("timestamp", OffsetDateTime.now().toString());
        logMap.put("level", "INFO");
        logMap.put("event", event);
        logMap.put("userId", userId);
        logMap.put("email", email);
        logMap.put("ip", ip);
        logMap.put("userAgent", userAgent);
        logMap.put("success", success);
        logMap.put("reason", reason);
        logMap.put("metadata", metadata);
        RequestIdFilter.getRequestId().ifPresent(id -> logMap.put("requestId", id));

        try {
            auditLog.info(objectMapper.writeValueAsString(logMap));
        } catch (Exception e) {
            log.warn("Failed to write structured audit log", e);
        }
    }

    private void persist(AuthAuditLog entry) {
        try { writer.save(entry); }
        catch (RuntimeException exception) {
            log.error("Could not persist security audit event {}", entry.getEvent(), exception);
        }
    }

    // Convenience methods for common events

    public void registerAttempt(String email, String ip, String userAgent, boolean success, String reason) {
        record("REGISTER_ATTEMPT", null, email, ip, userAgent, success, reason, null);
    }

    public void loginAttempt(String email, String ip, String userAgent, boolean success, String reason) {
        record("LOGIN_ATTEMPT", null, email, ip, userAgent, success, reason, null);
    }

    public void emailVerified(Long userId, String email, String ip) {
        record("EMAIL_VERIFIED", userId, email, ip, null, true, null, null);
    }

    public void passwordResetRequested(String email, String ip) {
        record("PASSWORD_RESET_REQUESTED", null, email, ip, null, true, null, null);
    }

    public void passwordResetCompleted(Long userId, String ip) {
        record("PASSWORD_RESET_COMPLETED", userId, null, ip, null, true, null, null);
    }

    public void passwordChanged(Long userId, String ip, String method) {
        Map<String, Object> meta = Map.of("method", method);
        record("PASSWORD_CHANGED", userId, null, ip, null, true, null, meta);
    }

    public void logout(Long userId, String ip, String jti) {
        Map<String, Object> meta = Map.of("jti", jti);
        record("LOGOUT", userId, null, ip, null, true, null, meta);
    }

    public void tokenRevoked(Long userId, String ip, String reason) {
        record("TOKEN_REVOKED", userId, null, ip, null, true, reason, null);
    }

    public void rateLimited(String ip, String endpoint, int limit, String window) {
        Map<String, Object> meta = Map.of("endpoint", endpoint, "limit", limit, "window", window);
        record("RATE_LIMITED", null, null, ip, null, false, "RATE_LIMIT_EXCEEDED", meta);
    }
}
