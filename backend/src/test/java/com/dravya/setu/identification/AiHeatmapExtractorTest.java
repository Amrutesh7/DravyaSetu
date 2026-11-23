package com.dravya.setu.identification;

import com.dravya.setu.ai.AiResponse;
import com.dravya.setu.storage.FetchedImage;
import com.dravya.setu.storage.RemoteImageFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiHeatmapExtractorTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk"
                    + "YPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    private RemoteImageFetcher fetcher;
    private AiHeatmapExtractor extractor;

    @BeforeEach
    void setUp() {
        fetcher = mock(RemoteImageFetcher.class);
        extractor = new AiHeatmapExtractor(fetcher);
    }

    @Test
    void decodesBase64HeatmapAndMapsOneBasedIndex() {
        AiResponse response = response(base64Heatmap(1, "image/png"));

        List<HeatmapPayload> payloads = extractor.extract(response, 2);

        assertEquals(1, payloads.size());
        assertEquals(0, payloads.get(0).imageIndex());
        assertEquals("image/png", payloads.get(0).contentType());
        assertTrue(java.util.Arrays.equals(PNG, payloads.get(0).bytes()));
        assertNull(payloads.get(0).sourceUrl());
    }

    @Test
    void acceptsDataUriPrefix() {
        String dataUri = "data:image/png;base64,"
                + Base64.getEncoder().encodeToString(PNG);
        AiResponse response = response(base64Heatmap(1, "image/png", dataUri));

        List<HeatmapPayload> payloads = extractor.extract(response, 1);

        assertEquals(1, payloads.size());
        assertEquals(0, payloads.get(0).imageIndex());
    }

    @Test
    void ignoresOutOfRangeAndInvalidHeatmaps() {
        AiResponse response = new AiResponse();
        AiResponse.Explanation explanation = new AiResponse.Explanation();
        explanation.setMethod("GRAD_CAM");
        explanation.setHeatmaps(List.of(
                base64Heatmap(3, "image/png"),
                base64Heatmap(1, "image/png", "not-base64!")));
        response.setExplanation(explanation);

        assertTrue(extractor.extract(response, 2).isEmpty());
    }

    @Test
    void fallsBackToLegacyUrlHeatmap() {
        AiResponse response = new AiResponse();
        AiResponse.Explanation explanation = new AiResponse.Explanation();
        explanation.setMethod("GRAD_CAM");
        explanation.setHeatmapUrl("https://ai.example/heatmap.png");
        response.setExplanation(explanation);
        when(fetcher.fetch("https://ai.example/heatmap.png"))
                .thenReturn(new FetchedImage(PNG, "image/png"));

        List<HeatmapPayload> payloads = extractor.extract(response, 1);

        assertEquals(1, payloads.size());
        assertEquals(0, payloads.get(0).imageIndex());
        assertEquals("https://ai.example/heatmap.png", payloads.get(0).sourceUrl());
    }

    private AiResponse response(AiResponse.Heatmap heatmap) {
        AiResponse response = new AiResponse();
        AiResponse.Explanation explanation = new AiResponse.Explanation();
        explanation.setMethod("GRAD_CAM");
        explanation.setHeatmaps(List.of(heatmap));
        response.setExplanation(explanation);
        return response;
    }

    private AiResponse.Heatmap base64Heatmap(int imageIndex, String mediaType) {
        return base64Heatmap(
                imageIndex,
                mediaType,
                Base64.getEncoder().encodeToString(PNG));
    }

    private AiResponse.Heatmap base64Heatmap(
            int imageIndex,
            String mediaType,
            String imageData) {
        AiResponse.Heatmap heatmap = new AiResponse.Heatmap();
        heatmap.setImageIndex(imageIndex);
        heatmap.setMediaType(mediaType);
        heatmap.setImageData(imageData);
        return heatmap;
    }
}
