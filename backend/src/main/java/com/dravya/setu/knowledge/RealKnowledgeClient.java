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

    private final RestClient restClient;
    private final String askPath;
    private final int readTimeoutMs;
    private final String upstreamHost;

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
        this.readTimeoutMs = readTimeoutMs;
        this.upstreamHost = java.net.URI.create(baseUrl).getHost();
    }

    @Override
    public KnowledgeResponse ask(KnowledgeRequest request) {
        long started = System.nanoTime();
        log.info("Knowledge call started: request_id={} host={} read_timeout_ms={}",
                request.getRequestId(), upstreamHost, readTimeoutMs);
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

            log.info("Knowledge call completed: request_id={} duration_ms={}",
                    request.getRequestId(), elapsedMillis(started));
            return response;

        } catch (RestClientResponseException e) {

            log.warn(
                    "Knowledge service returned HTTP {} for request_id={} duration_ms={}",
                    e.getStatusCode().value(),
                    request.getRequestId(),
                    elapsedMillis(started)
            );

            throw new KnowledgeServiceException(
                    "Knowledge service request failed with HTTP "
                            + e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {

            log.warn(
                    "Knowledge service connection failed: request_id={} duration_ms={} reason={} cause={}",
                    request.getRequestId(),
                    elapsedMillis(started),
                    networkFailure(e.getMostSpecificCause()),
                    e.getMostSpecificCause().getClass().getSimpleName()
            );

            throw new KnowledgeServiceException(
                    "Knowledge service is unreachable or timed out",
                    e
            );

        } catch (RestClientException e) {
            log.warn("Knowledge response could not be read: request_id={} duration_ms={} cause={}",
                    request.getRequestId(), elapsedMillis(started), e.getMostSpecificCause().getClass().getSimpleName());
            throw new KnowledgeServiceException(
                    "Knowledge service request failed",
                    e
            );
        }
    }

    private static long elapsedMillis(long started) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static String networkFailure(Throwable cause) {
        if (cause instanceof java.net.SocketTimeoutException) {
            String message = cause.getMessage();
            return message != null && message.toLowerCase(java.util.Locale.ROOT).contains("connect")
                    ? "CONNECT_TIMEOUT" : "READ_TIMEOUT";
        }
        if (cause instanceof java.net.UnknownHostException) return "DNS_ERROR";
        if (cause instanceof javax.net.ssl.SSLException) return "TLS_ERROR";
        return "NETWORK_ERROR";
    }
}
