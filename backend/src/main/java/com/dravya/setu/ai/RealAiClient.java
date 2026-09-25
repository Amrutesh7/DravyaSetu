package com.dravya.setu.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Component
@Profile("real-ai")
public class RealAiClient implements AiClient {

    private final RestClient restClient;
    private final String identifyPath;

    public RealAiClient(
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.identify-path}") String identifyPath,
            @Value("${app.ai.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${app.ai.read-timeout-ms}") int readTimeoutMs) {

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        this.identifyPath = identifyPath;
    }

    @Override
    public AiResponse identify(AiRequest request) {

        try {
            AiResponse response = restClient.post()
                    .uri(identifyPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiResponse.class);

            if (response == null) {
                throw new AiServiceException(
                        "AI service returned an empty response");
            }

            return response;

        } catch (RestClientException e) {

            throw new AiServiceException(
                    "AI service request failed",
                    e
            );
        }
    }
}