package com.dravya.setu.identification;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record IdentificationHistoryResponse(
        Long id, String requestId, String status, String plantId, Double confidence,
        String modelVersion, OffsetDateTime createdAt, List<Image> images) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Image(
            String plantPart,
            Integer sequenceNumber,
            String imageReference,
            List<String> heatmapUrls) { }
}
