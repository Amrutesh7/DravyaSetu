package com.dravya.setu.knowledge;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Profile("!real-knowledge")
public class MockKnowledgeClient implements KnowledgeClient {

    @Override
    public KnowledgeResponse ask(KnowledgeRequest request) {

        KnowledgeResponse response =
                new KnowledgeResponse();

        response.setRequestId(
                UUID.randomUUID().toString()
        );

        response.setAnswer(
                "This is a temporary knowledge-service response for "
                        + request.getPlantCode()
                        + "."
        );

        response.setLanguage(
                request.getLanguage()
        );

        response.setSources(
                List.of()
        );

        response.setVoiceOutput(
                null
        );

        response.setWarnings(
                List.of()
        );

        response.setErrors(
                List.of()
        );

        return response;
    }
}