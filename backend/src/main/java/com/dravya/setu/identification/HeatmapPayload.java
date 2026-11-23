package com.dravya.setu.identification;

/**
 * Transport-neutral heatmap payload. {@code bytes} is set when the AI supplied
 * raw image data; {@code sourceUrl} is set when the AI supplied a URL.
 */
public record HeatmapPayload(
        int imageIndex,
        byte[] bytes,
        String contentType,
        String sourceUrl) {
}
