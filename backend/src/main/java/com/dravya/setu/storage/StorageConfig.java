package com.dravya.setu.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.storage", name = "enabled", havingValue = "true")
    public S3Client s3Client(StorageProperties properties) {
        requireCompleteConfiguration(properties);
        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(s3Configuration(properties))
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.storage", name = "enabled", havingValue = "true")
    public S3Presigner s3Presigner(StorageProperties properties) {
        requireCompleteConfiguration(properties);
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(s3Configuration(properties))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.storage", name = "enabled", havingValue = "true")
    public StorageService s3StorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            StorageProperties properties) {
        return new S3StorageService(s3Client, s3Presigner, properties);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.storage",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    public StorageService noOpStorageService() {
        return new NoOpStorageService();
    }

    private static StaticCredentialsProvider credentials(StorageProperties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                properties.getAccessKeyId(),
                properties.getSecretAccessKey()));
    }

    private static S3Configuration s3Configuration(StorageProperties properties) {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(properties.isPathStyle())
                .build();
    }

    private static void requireCompleteConfiguration(StorageProperties properties) {
        require(properties.getEndpoint(), "B2_ENDPOINT");
        require(properties.getRegion(), "B2_REGION");
        require(properties.getBucket(), "B2_BUCKET");
        require(properties.getAccessKeyId(), "B2_APPLICATION_KEY_ID");
        require(properties.getSecretAccessKey(), "B2_APPLICATION_KEY");
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "B2 storage is enabled but " + name + " is missing");
        }
    }
}
