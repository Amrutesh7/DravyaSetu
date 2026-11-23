package com.dravya.setu.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the private Backblaze B2 bucket.
 *
 * <p>Object keys are persisted; signed GET URLs are generated on demand and are
 * never stored.</p>
 */
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private boolean enabled;
    private String endpoint;
    private String region;
    private String bucket;
    private String accessKeyId;
    private String secretAccessKey;
    private boolean pathStyle = true;
    private Duration presignTtl = Duration.ofMinutes(60);
    private Duration aiPresignTtl = Duration.ofMinutes(15);
    private long maxHeatmapBytes = 5L * 1024 * 1024;
    private Duration heatmapTimeout = Duration.ofSeconds(10);
    private List<String> heatmapAllowedHosts = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    public boolean isPathStyle() {
        return pathStyle;
    }

    public void setPathStyle(boolean pathStyle) {
        this.pathStyle = pathStyle;
    }

    public Duration getPresignTtl() {
        return presignTtl;
    }

    public void setPresignTtl(Duration presignTtl) {
        this.presignTtl = presignTtl;
    }

    public Duration getAiPresignTtl() {
        return aiPresignTtl;
    }

    public void setAiPresignTtl(Duration aiPresignTtl) {
        this.aiPresignTtl = aiPresignTtl;
    }

    public long getMaxHeatmapBytes() {
        return maxHeatmapBytes;
    }

    public void setMaxHeatmapBytes(long maxHeatmapBytes) {
        this.maxHeatmapBytes = maxHeatmapBytes;
    }

    public Duration getHeatmapTimeout() {
        return heatmapTimeout;
    }

    public void setHeatmapTimeout(Duration heatmapTimeout) {
        this.heatmapTimeout = heatmapTimeout;
    }

    public List<String> getHeatmapAllowedHosts() {
        return heatmapAllowedHosts;
    }

    public void setHeatmapAllowedHosts(List<String> heatmapAllowedHosts) {
        this.heatmapAllowedHosts = heatmapAllowedHosts;
    }
}
