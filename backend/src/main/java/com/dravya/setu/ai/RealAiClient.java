package com.dravya.setu.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Profile("real-ai")
public class RealAiClient implements AiClient {

    private final RestClient restClient;
    private final String identifyPath;
    private final String imageTransport;
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 200;

    public RealAiClient(
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.identify-path}") String identifyPath,
            @Value("${app.ai.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${app.ai.read-timeout-ms}") int readTimeoutMs,
            @Value("${app.ai.image-transport:multipart}") String imageTransport) {

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        this.identifyPath = identifyPath;
        this.imageTransport = imageTransport;
    }

    @Override
    public AiResponse identify(AiRequest request) {
        if (request == null) {
            throw new AiServiceException("AI request is required");
        }

        boolean urlMode = "url".equalsIgnoreCase(imageTransport)
                && request.getImageUrls() != null
                && !request.getImageUrls().isEmpty();

        RestClientException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                AiResponse response = urlMode
                        ? callWithUrls(request)
                        : callWithMultipart(request);

                if (response == null) {
                    throw new AiServiceException(
                            "AI service returned an empty response");
                }

                return response;
            } catch (IOException exception) {
                throw new AiServiceException(
                        "AI request image could not be read", exception);
            } catch (RestClientException exception) {
                lastFailure = exception;
                if (!isRetryable(exception) || attempt == MAX_ATTEMPTS) {
                    break;
                }
                waitBeforeRetry(attempt);
            }
        }
        throw new AiServiceException(
                "AI service request failed after " + MAX_ATTEMPTS + " attempts",
                lastFailure);
    }

    private AiResponse callWithUrls(AiRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("request_id", request.getRequestId());
        body.put("image", request.getImageUrls());
        if (request.getPlantPart() != null && !request.getPlantPart().isBlank()) {
            body.put("plant_part", request.getPlantPart());
        }

        return restClient.post()
                .uri(identifyPath)
                .header("X-Request-Id", request.getRequestId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(AiResponse.class);
    }

    private AiResponse callWithMultipart(AiRequest request) throws IOException {
        if (request.getImages() == null || request.getImages().isEmpty()) {
            throw new AiServiceException("AI request requires at least one image");
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (MultipartFile file : request.getImages()) {
            ByteArrayResource image = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() == null
                            ? "plant-image" : file.getOriginalFilename();
                }
            };
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(file.getContentType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(file.getContentType()));
            body.add("images", new HttpEntity<>(image, headers));
        }
        body.add("request_id", request.getRequestId());

        return restClient.post()
                .uri(identifyPath)
                .header("X-Request-Id", request.getRequestId())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(AiResponse.class);
    }

    private boolean isRetryable(RestClientException exception) {
        return exception instanceof ResourceAccessException
                || (exception instanceof HttpServerErrorException serverError
                && (serverError.getStatusCode().value() == 502
                || serverError.getStatusCode().value() == 503
                || serverError.getStatusCode().value() == 504));
    }

    private void waitBeforeRetry(int attempt) {
        try {
            Thread.sleep(INITIAL_BACKOFF_MS * (1L << (attempt - 1)));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiServiceException("AI service retry interrupted", exception);
        }
    }
}
