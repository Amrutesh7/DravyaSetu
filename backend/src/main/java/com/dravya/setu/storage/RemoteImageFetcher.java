package com.dravya.setu.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Downloads AI-provided heatmap URLs and validates them before they reach B2.
 */
@Component
public class RemoteImageFetcher {

    private static final Logger log = LoggerFactory.getLogger(RemoteImageFetcher.class);
    private static final int MAX_REDIRECTS = 3;

    private final StorageProperties properties;
    private final HttpClient httpClient;

    public RemoteImageFetcher(StorageProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getHeatmapTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public FetchedImage fetch(String sourceUrl) {
        URI uri = validate(sourceUrl);

        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(properties.getHeatmapTimeout())
                    .header("Accept", "image/*")
                    .GET()
                    .build();

            HttpResponse<InputStream> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new StorageException("Heatmap download was interrupted", exception);
            } catch (IOException exception) {
                throw new StorageException("Could not download heatmap", exception);
            }

            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location").orElse(null);
                if (location == null || location.isBlank()) {
                    throw new StorageException("Heatmap redirect is missing a Location header");
                }
                uri = validate(uri.resolve(location).toString());
                continue;
            }
            if (status < 200 || status >= 300) {
                throw new StorageException("Heatmap download failed with status " + status);
            }

            String declaredType = response.headers()
                    .firstValue("Content-Type")
                    .orElse(null);
            byte[] bytes;
            try (InputStream input = response.body()) {
                bytes = readLimited(input, properties.getMaxHeatmapBytes());
            } catch (IOException exception) {
                throw new StorageException("Could not read heatmap response", exception);
            }

            String detectedType = ImageContent.detectContentType(bytes);
            String normalizedDeclared = ImageContent.normalizeContentType(declaredType);
            String finalType = detectedType != null ? detectedType : normalizedDeclared;

            if (finalType == null || !ImageContent.SUPPORTED.contains(finalType)
                    || !ImageContent.hasMatchingSignature(finalType, bytes)) {
                throw new StorageException(
                        "Heatmap is not a supported JPEG, PNG, or WebP image");
            }

            return new FetchedImage(bytes, finalType);
        }

        throw new StorageException("Heatmap download exceeded the redirect limit");
    }

    private URI validate(String sourceUrl) {
        URI uri;
        try {
            uri = URI.create(sourceUrl);
        } catch (IllegalArgumentException exception) {
            throw new StorageException("Heatmap URL is invalid", exception);
        }

        String scheme = uri.getScheme();
        if (scheme == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new StorageException("Heatmap URL must use http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new StorageException("Heatmap URL has no host");
        }

        List<String> allowedHosts = properties.getHeatmapAllowedHosts();
        if (allowedHosts != null && !allowedHosts.isEmpty()
                && allowedHosts.stream().noneMatch(host -> host.equalsIgnoreCase(uri.getHost()))) {
            throw new StorageException("Heatmap host is not allowed: " + uri.getHost());
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (isBlocked(address)) {
                    throw new StorageException(
                            "Heatmap URL resolves to a blocked address: " + address.getHostAddress());
                }
            }
        } catch (UnknownHostException exception) {
            throw new StorageException("Heatmap host could not be resolved", exception);
        }

        return uri;
    }

    private boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] raw = address.getAddress();
        // IPv6 unique local addresses (fc00::/7) are not covered by isSiteLocalAddress.
        return raw.length == 16 && (raw[0] & 0xfe) == 0xfc;
    }

    private byte[] readLimited(InputStream input, long maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new StorageException("Heatmap exceeds the maximum allowed size");
            }
            output.write(buffer, 0, read);
        }
        log.debug("Downloaded heatmap of {} bytes", total);
        return output.toByteArray();
    }

    static Duration defaultTimeout() {
        return Duration.ofSeconds(10);
    }
}
