package com.dravya.setu.chatbot;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One persisted chat session belonging to the caller.
 *
 * <p>{@code plant_id} is the shared {@code PLANT_###} code, not the database identifier.</p>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatSessionSummary(
        UUID sessionId,
        String plantId,
        String language,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
