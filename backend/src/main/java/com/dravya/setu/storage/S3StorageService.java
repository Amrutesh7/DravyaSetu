package com.dravya.setu.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

public class S3StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    public S3StorageService(
            S3Client s3Client,
            S3Presigner presigner,
            StorageProperties properties) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String upload(String key, byte[] content, String contentType) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .contentType(contentType)
                    .contentLength((long) content.length)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(content));
            return key;
        } catch (SdkException exception) {
            throw new StorageException("Could not upload object to B2: " + key, exception);
        }
    }

    @Override
    public String displayUrl(String key) {
        return presignedUrl(key, properties.getPresignTtl());
    }

    @Override
    public String aiUrl(String key) {
        return presignedUrl(key, properties.getAiPresignTtl());
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(builder -> builder
                    .bucket(properties.getBucket())
                    .key(key));
        } catch (SdkException exception) {
            log.warn("Could not delete B2 object {}: {}", key, exception.getMessage());
        }
    }

    private String presignedUrl(String key, Duration ttl) {
        try {
            GetObjectRequest getObject = GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build();
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(getObject)
                    .build();
            return presigner.presignGetObject(presignRequest).url().toString();
        } catch (SdkException exception) {
            throw new StorageException("Could not create signed URL for B2 object: " + key, exception);
        }
    }
}
