package com.dravya.setu.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * HTTP client for Person 3's deployed knowledge service.
 *
 * <p>Person 3 hosts the service on a free Render instance that sleeps when idle, so the first
 * request after a quiet period can take tens of seconds. The read timeout is therefore much
 * more generous than the AI client's and is configurable through
 * {@code KNOWLEDGE_READ_TIMEOUT_MS}.</p>
 */
@Component
@Profile("real-knowledge")
public class RealKnowledgeClient implements KnowledgeClient {

    private static final Logger log =
            LoggerFactory.getLogger(RealKnowledgeClient.class);

    private static final int MAX_LOGGED_BODY = 500;

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
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .header("X-Request-Id", request.getRequestId())
                            .body(request)
                            .retrieve()
                            .body(KnowledgeResponse.class);

            if (response == null) {
                throw new KnowledgeServiceException(
                        "Knowledge service returned an empty response"
                );
            }

            return response;

        } catch (RestClientResponseException e) {

            log.warn(
                    "Knowledge service returned HTTP {} for request_id={}: {}",
                    e.getStatusCode().value(),
                    request.getRequestId(),
                    abbreviate(e.getResponseBodyAsString())
            );

            throw new KnowledgeServiceException(
                    "Knowledge service request failed with HTTP "
                            + e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {

            log.warn(
                    "Knowledge service unreachable or timed out for request_id={}: {}",
                    request.getRequestId(),
                    e.getMessage()
            );

            throw new KnowledgeServiceException(
                    "Knowledge service is unreachable or timed out",
                    e
            );

        } catch (RestClientException e) {

            throw new KnowledgeServiceException(
                    "Knowledge service request failed",
                    e
            );
        }
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.strip();
        return trimmed.length() <= MAX_LOGGED_BODY
                ? trimmed
                : trimmed.substring(0, MAX_LOGGED_BODY) + "...";
    }
}
