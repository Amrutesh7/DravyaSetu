package com.dravya.setu.ai;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!real-ai")
public class MockAiClient implements AiClient {

    @Override
    public AiResponse identify(AiRequest request) {

        AiResponse response = new AiResponse();

        response.setRequestId(request.getRequestId());

        response.setModelVersion("mock-plant-v1.0");

        /*
         * Image quality
         */
        AiResponse.ImageQuality imageQuality =
                new AiResponse.ImageQuality();

        imageQuality.setAcceptable(true);
        imageQuality.setIssues(List.of());

        response.setImageQuality(imageQuality);

        /*
         * Main identification
         */
        AiResponse.Identification identification =
                new AiResponse.Identification();

        identification.setStatus("KNOWN");
        identification.setPlantId("PLANT_001");
        identification.setConfidence(0.92);

        response.setIdentification(identification);

        /*
         * Alternatives
         */
        AiResponse.Alternative alternative =
                new AiResponse.Alternative();

        alternative.setPlantId("PLANT_002");
        alternative.setCommonName("Alternative Test Plant");
        alternative.setScientificName("Testus alternativus");
        alternative.setConfidence(0.05);

        response.setAlternatives(
                List.of(alternative)
        );

        /*
         * Similar species
         */
        AiResponse.SimilarSpecies similarSpecies =
                new AiResponse.SimilarSpecies();

        similarSpecies.setPlantId("PLANT_002");
        similarSpecies.setName("Alternative Test Plant");
        similarSpecies.setSimilarity(0.87);

        response.setSimilarSpecies(
                List.of(similarSpecies)
        );

        /*
         * Explainability
         */
        AiResponse.Explanation explanation =
                new AiResponse.Explanation();

        explanation.setMethod("GRAD_CAM");
        explanation.setHeatmapUrl(
                "https://example.com/mock-heatmap.jpg"
        );

        response.setExplanation(explanation);

        /*
         * Multi-image
         */
        int imageCount = Math.max(
                1,
                Math.max(
                        request.getImages() == null ? 0 : request.getImages().size(),
                        request.getImageUrls() == null ? 0 : request.getImageUrls().size()));

        AiResponse.MultiImage multiImage =
                new AiResponse.MultiImage();

        multiImage.setUsed(imageCount > 1);
        multiImage.setImageCount(imageCount);

        response.setMultiImage(multiImage);

        /*
         * Warnings
         */
        response.setWarnings(List.of());

        return response;
    }
}
