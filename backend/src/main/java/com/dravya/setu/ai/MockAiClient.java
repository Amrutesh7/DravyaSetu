package com.dravya.setu.ai;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("!real-ai")
public class MockAiClient implements AiClient {

    @Override
    public AiResponse identify(AiRequest request) {

        return new AiResponse(
                "PLANT_001",
                "SUCCESS",
                0.92,
                "mock-model-v1"
        );
    }
}