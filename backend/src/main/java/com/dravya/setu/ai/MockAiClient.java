package com.dravya.setu.ai;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Profile("!real-ai")
public class MockAiClient implements AiClient {

    @Override
    public AiResponse identify(AiRequest request) {

        AiResponse response = new AiResponse();

        /*
         * The mock generates a request ID because the current temporary
         * AiRequest contract does not yet define a request_id field.
         *
         * Once Person 1's final AI request contract is frozen, this can
         * be changed to pass through the request ID from AiRequest.
         */
        response.setRequestId(UUID.randomUUID().toString());

        response.setModelVersion("mock-model-v2");

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
         *
         * The current backend endpoint accepts one image, so the mock
         * represents the current request as a single-image prediction.
         */
        AiResponse.MultiImage multiImage =
                new AiResponse.MultiImage();

        multiImage.setUsed(false);
        multiImage.setImageCount(1);

        response.setMultiImage(multiImage);

        /*
         * Warnings
         */
        response.setWarnings(List.of());

        return response;
    }
}