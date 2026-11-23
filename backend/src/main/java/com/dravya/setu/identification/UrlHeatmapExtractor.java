package com.dravya.setu.identification;

import com.dravya.setu.ai.AiResponse;
import com.dravya.setu.storage.FetchedImage;
import com.dravya.setu.storage.RemoteImageFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the legacy {@code explanation.heatmap_url} (single) or
 * {@code explanation.heatmap_urls} array. A failed download is returned as a
 * source-URL fallback so the identification itself still succeeds.
 *
 * <p>This is a helper used by {@link AiHeatmapExtractor}; it is not a bean on
 * its own so that only one {@link HeatmapExtractor} is injected.</p>
 */
public class UrlHeatmapExtractor implements HeatmapExtractor {

    private static final Logger log = LoggerFactory.getLogger(UrlHeatmapExtractor.class);

    private final RemoteImageFetcher fetcher;

    public UrlHeatmapExtractor(RemoteImageFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public List<HeatmapPayload> extract(AiResponse response, int imageCount) {
        if (response == null || response.getExplanation() == null) {
            return List.of();
        }

        List<String> urls = response.getExplanation().getHeatmapUrls();
        if (urls != null && !urls.isEmpty()) {
            List<HeatmapPayload> payloads = new ArrayList<>();
            int limit = Math.min(urls.size(), imageCount);
            for (int index = 0; index < limit; index++) {
                payloads.add(fetch(index, urls.get(index)));
            }
            return payloads;
        }

        String url = response.getExplanation().getHeatmapUrl();
        if (url != null && !url.isBlank()) {
            return List.of(fetch(0, url));
        }
        return List.of();
    }

    private HeatmapPayload fetch(int imageIndex, String url) {
        if (url == null || url.isBlank()) {
            return new HeatmapPayload(imageIndex, null, null, null);
        }
        try {
            FetchedImage image = fetcher.fetch(url);
            return new HeatmapPayload(imageIndex, image.bytes(), image.contentType(), url);
        } catch (RuntimeException exception) {
            log.warn("Could not archive heatmap for image {}: {}",
                    imageIndex, exception.getMessage());
            return new HeatmapPayload(imageIndex, null, null, url);
        }
    }
}
