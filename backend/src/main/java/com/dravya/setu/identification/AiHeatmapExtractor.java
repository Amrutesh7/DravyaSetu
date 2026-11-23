package com.dravya.setu.identification;

import com.dravya.setu.ai.AiResponse;
import com.dravya.setu.storage.ImageContent;
import com.dravya.setu.storage.RemoteImageFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Primary heatmap extractor for the deployed AI service.
 *
 * <p>The service returns base64-encoded heatmaps in
 * {@code explanation.heatmaps[]}, each carrying a 1-based
 * {@code image_index}. Older deployments that return URLs in
 * {@code explanation.heatmap_url} / {@code heatmap_urls} are still supported as
 * a fallback.</p>
 */
@Component
public class AiHeatmapExtractor implements HeatmapExtractor {

    private static final Logger log = LoggerFactory.getLogger(AiHeatmapExtractor.class);

    private final UrlHeatmapExtractor urlExtractor;

    public AiHeatmapExtractor(RemoteImageFetcher fetcher) {
        this.urlExtractor = new UrlHeatmapExtractor(fetcher);
    }

    @Override
    public List<HeatmapPayload> extract(AiResponse response, int imageCount) {
        if (response == null || response.getExplanation() == null) {
            return List.of();
        }

        List<AiResponse.Heatmap> heatmaps = response.getExplanation().getHeatmaps();
        if (heatmaps != null && !heatmaps.isEmpty()) {
            List<HeatmapPayload> payloads = new ArrayList<>();
            for (AiResponse.Heatmap heatmap : heatmaps) {
                HeatmapPayload payload = decode(heatmap, imageCount);
                if (payload != null) {
                    payloads.add(payload);
                }
            }
            return payloads;
        }

        return urlExtractor.extract(response, imageCount);
    }

    private HeatmapPayload decode(AiResponse.Heatmap heatmap, int imageCount) {
        if (heatmap == null || heatmap.getImageData() == null
                || heatmap.getImageData().isBlank()) {
            return null;
        }

        Integer rawIndex = heatmap.getImageIndex();
        int imageIndex = rawIndex == null ? 0 : rawIndex - 1;
        if (imageIndex < 0 || imageIndex >= imageCount) {
            log.warn("Ignoring heatmap for out-of-range image index {} (image count {})",
                    rawIndex, imageCount);
            return null;
        }

        String encoded = stripDataUriPrefix(heatmap.getImageData().trim());
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            log.warn("Ignoring heatmap for image {}: invalid base64 data",
                    rawIndex);
            return null;
        }
        if (bytes.length == 0) {
            log.warn("Ignoring empty heatmap for image {}", rawIndex);
            return null;
        }

        String declaredType = ImageContent.normalizeContentType(heatmap.getMediaType());
        String detectedType = ImageContent.detectContentType(bytes);
        String contentType = detectedType != null ? detectedType : declaredType;

        if (contentType == null
                || !ImageContent.SUPPORTED.contains(contentType)
                || !ImageContent.hasMatchingSignature(contentType, bytes)) {
            log.warn("Ignoring heatmap for image {}: unsupported or invalid image data",
                    rawIndex);
            return null;
        }

        return new HeatmapPayload(imageIndex, bytes, contentType, null);
    }

    private String stripDataUriPrefix(String value) {
        if (!value.regionMatches(true, 0, "data:", 0, 5)) {
            return value;
        }
        int comma = value.indexOf(',');
        return comma < 0 ? value : value.substring(comma + 1);
    }
}
