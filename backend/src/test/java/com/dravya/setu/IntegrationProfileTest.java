package com.dravya.setu;

import com.dravya.setu.ai.*;
import com.dravya.setu.knowledge.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;

class IntegrationProfileTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RealAiClient.class, MockAiClient.class,
                    RealKnowledgeClient.class, MockKnowledgeClient.class)
            .withPropertyValues("app.ai.base-url=http://localhost:1", "app.ai.identify-path=/ai/analyze",
                    "app.ai.connect-timeout-ms=1000", "app.ai.read-timeout-ms=1000",
                    "app.knowledge.base-url=http://localhost:1", "app.knowledge.ask-path=/knowledge/chat",
                    "app.knowledge.connect-timeout-ms=1000", "app.knowledge.read-timeout-ms=1000");

    @Test void deployedProfilesSelectOnlyRealClientsEvenIfTheirUrlsAreUnavailable() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("real-ai", "real-knowledge"))
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(AiClient.class).size());
                    assertEquals(1, context.getBeansOfType(KnowledgeClient.class).size());
                    assertInstanceOf(RealAiClient.class, context.getBean(AiClient.class));
                    assertInstanceOf(RealKnowledgeClient.class, context.getBean(KnowledgeClient.class));
                    assertEquals(0, context.getBeansOfType(MockAiClient.class).size());
                    assertEquals(0, context.getBeansOfType(MockKnowledgeClient.class).size());
                });
    }
}
