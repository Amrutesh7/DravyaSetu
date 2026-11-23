package com.dravya.setu.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, Long> {

    Page<AuthAuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<AuthAuditLog> findByEventOrderByCreatedAtDesc(String event, Pageable pageable);

    Page<AuthAuditLog> findByEmailOrderByCreatedAtDesc(String email, Pageable pageable);

    Page<AuthAuditLog> findByIpOrderByCreatedAtDesc(String ip, Pageable pageable);

    @Query("SELECT a FROM AuthAuditLog a WHERE a.createdAt >= :since ORDER BY a.createdAt DESC")
    Page<AuthAuditLog> findSince(@Param("since") OffsetDateTime since, Pageable pageable);

    List<AuthAuditLog> findTop100ByOrderByCreatedAtDesc();
}