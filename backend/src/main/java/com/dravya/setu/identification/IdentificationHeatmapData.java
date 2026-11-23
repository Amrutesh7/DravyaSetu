package com.dravya.setu.identification;

/**
 * Persistence instruction for one heatmap. {@code storageKey} is set when the
 * image was archived in B2; {@code sourceUrl} is the original AI URL fallback.
 */
public record IdentificationHeatmapData(
        int imageIndex,
        String storageKey,
        String sourceUrl,
        String contentType,
        int sequenceNumber) {
}
