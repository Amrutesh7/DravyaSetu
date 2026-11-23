package com.dravya.setu.chatbot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    @org.springframework.data.jpa.repository.Query("select m from ChatMessage m where m.sessionId = :sessionId order by m.createdAt asc, m.id asc")
    Page<ChatMessage> findBySessionIdOrderByCreatedAtAsc(@org.springframework.data.repository.query.Param("sessionId") UUID sessionId, Pageable pageable);
    @org.springframework.data.jpa.repository.Query("select m from ChatMessage m where m.sessionId = :sessionId order by m.createdAt desc, m.id desc")
    List<ChatMessage> findBySessionIdOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("sessionId") UUID sessionId, Pageable pageable);
}
