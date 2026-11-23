package com.dravya.setu.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthAuditWriter {
    private final AuthAuditLogRepository repository;
    public AuthAuditWriter(AuthAuditLogRepository repository) { this.repository = repository; }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AuthAuditLog entry) { repository.saveAndFlush(entry); }
}
