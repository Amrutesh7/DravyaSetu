package com.dravya.setu.chatbot;

import com.dravya.setu.knowledge.KnowledgeSource;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * One persisted message in a chat session. Roles are lower case ({@code user}/{@code assistant})
 * for the public API; the database stores them upper case.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatMessageResponse(
        Long id,
        String role,
        String content,
        List<KnowledgeSource> sources,
        OffsetDateTime createdAt) {
}
