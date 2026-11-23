package com.dravya.setu.identification;

import com.dravya.setu.ai.AiResponse;

import java.util.List;

/**
 * Extracts heatmap bytes from an AI response. The current implementation reads
 * URL-based heatmaps; a future raw-file/multipart transport only needs a new
 * implementation of this interface.
 */
public interface HeatmapExtractor {

    List<HeatmapPayload> extract(AiResponse response, int imageCount);
}
