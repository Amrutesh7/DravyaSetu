package com.dravya.setu.knowledge;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Default knowledge client. It mirrors the deployed response shape exactly so that the public
 * API contract is the same whether the real service is wired in or not.
 */
@Component
@Profile("!real-knowledge")
public class MockKnowledgeClient implements KnowledgeClient {

    @Override
    public KnowledgeResponse ask(KnowledgeRequest request) {

        KnowledgeResponse response =
                new KnowledgeResponse();

        response.setRequestId(request.getRequestId());

        response.setSessionId(
                request.getSessionId()
        );

        response.setDetectedLanguage(
                request.getInputLanguage()
        );

        // Text input never carries a transcript.
        response.setTranscript(
                null
        );

        response.setAnswer(
                "This is a temporary knowledge-service response for "
                        + (request.getPlantId() == null ? "general plant knowledge" : request.getPlantId())
                        + "."
        );

        response.setLanguage(
                request.getResponseLanguage()
        );

        response.setSources(
                List.of(new KnowledgeSource(
                        "SRC_MOCK_001",
                        "Mock medicinal plant reference",
                        "mock-plant-reference.md"
                ))
        );

        if (request.isGenerateAudio()) {
            response.setVoiceOutput(Map.of(
                    "available", false,
                    "message", "Mock audio output is not generated"
            ));
            response.setWarnings(List.of(
                    "Voice output is mocked during local development"
            ));
        } else {
            response.setVoiceOutput(
                    null
            );
            response.setWarnings(
                    List.of()
            );
        }

        return response;
    }
}
