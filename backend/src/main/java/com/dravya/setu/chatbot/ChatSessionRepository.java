package com.dravya.setu.chatbot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    Optional<ChatSession> findByIdAndUserId(UUID id, Long userId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from ChatSession s where s.id = :id and s.userId = :userId")
    Optional<ChatSession> findByIdAndUserIdForUpdate(
            @org.springframework.data.repository.query.Param("id") UUID id,
            @org.springframework.data.repository.query.Param("userId") Long userId);
    @org.springframework.data.jpa.repository.Query("select s from ChatSession s where s.userId = :userId order by s.updatedAt desc, s.id desc")
    Page<ChatSession> findByUserIdOrderByUpdatedAtDesc(@org.springframework.data.repository.query.Param("userId") Long userId, Pageable pageable);
}
