package com.dravya.setu.storage;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
public class R2StorageService {

    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final S3Client s3Client;
    private final String bucket;
    private final String publicUrl;

    public R2StorageService(R2StorageProperties properties) {
        this.bucket = properties.getBucket();
        this.publicUrl = properties.getPublicUrl();

        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getAccessKey(),
                properties.getSecretKey()
        );

        this.s3Client = S3Client.builder()
                .endpointOverride(java.net.URI.create(
                        String.format("https://%s.r2.cloudflarestorage.com", properties.getAccountId())
                ))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(properties.getRegion()))
                .build();
    }

    /**
     * Upload an image file to R2 and return the public URL.
     *
     * @param file the image file to upload
     * @param folder the folder path (e.g., "identifications/userId")
     * @return the public URL of the uploaded image
     */
    public String uploadImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }

        String contentType = file.getContentType();
        if (!IMAGE_EXTENSIONS.containsKey(contentType)) {
            throw new IllegalArgumentException(
                    "File must be a JPEG, PNG, or WebP image");
        }

        try {
            byte[] imageBytes = file.getBytes();
            if (!hasExpectedMagicBytes(contentType, imageBytes)) {
                throw new IllegalArgumentException(
                        "File content does not match its image type");
            }

            String key = folder + "/" + UUID.randomUUID()
                    + IMAGE_EXTENSIONS.get(contentType);
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();

            PutObjectResponse response = s3Client.putObject(
                    putRequest,
                    RequestBody.fromBytes(imageBytes)
            );

            if (response.sdkHttpResponse().isSuccessful()) {
                return publicUrl + "/" + key;
            } else {
                throw new RuntimeException("Failed to upload to R2: " + response.sdkHttpResponse().statusCode());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file", e);
        }
    }

    /**
     * Delete an image from R2 by its public URL.
     */
    public void deleteImage(String publicImageUrl) {
        String prefix = publicUrl + "/";
        if (publicUrl == null || publicImageUrl == null || !publicImageUrl.startsWith((prefix))){
            return;
        }

        String key = publicImageUrl.substring(prefix.length());
        s3Client.deleteObject(b -> b.bucket(bucket).key(key));
    }

    private boolean hasExpectedMagicBytes(String contentType, byte[] bytes) {
        if (bytes == null) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xFF) == 0xFF
                    && (bytes[1] & 0xFF) == 0xD8
                    && (bytes[2] & 0xFF) == 0xFF;
            case "image/png" -> bytes.length >= 8
                    && (bytes[0] & 0xFF) == 0x89
                    && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                    && bytes[4] == 0x0D && bytes[5] == 0x0A
                    && bytes[6] == 0x1A && bytes[7] == 0x0A;
            case "image/webp" -> bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I'
                    && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E'
                    && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }
}
