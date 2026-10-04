package com.dravya.setu.knowledge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Profile("real-knowledge")
public class RealKnowledgeClient implements KnowledgeClient {

    private final RestClient restClient;
    private final String askPath;

    public RealKnowledgeClient(
            @Value("${app.knowledge.base-url}") String baseUrl,
            @Value("${app.knowledge.ask-path}") String askPath,
            @Value("${app.knowledge.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${app.knowledge.read-timeout-ms}") int readTimeoutMs) {

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        this.askPath = askPath;
    }

    @Override
    public KnowledgeResponse ask(KnowledgeRequest request) {

        try {

            KnowledgeResponse response =
                    restClient.post()
                            .uri(askPath)
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(request)
                            .retrieve()
                            .body(KnowledgeResponse.class);

            if (response == null) {
                throw new KnowledgeServiceException(
                        "Knowledge service returned an empty response"
                );
            }

            return response;

        } catch (RestClientException e) {

            throw new KnowledgeServiceException(
                    "Knowledge service request failed",
                    e
            );
        }
    }
}