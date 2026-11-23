package com.dravya.setu.storage;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared image content-type and signature handling for uploads and heatmaps.
 */
public final class ImageContent {

    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String WEBP = "image/webp";

    public static final Set<String> SUPPORTED = Set.of(JPEG, PNG, WEBP);

    private static final Map<String, String> EXTENSIONS = Map.of(
            JPEG, "jpg",
            PNG, "png",
            WEBP, "webp"
    );

    private ImageContent() {
    }

    public static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        String value = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "image/jpg", "image/pjpeg" -> JPEG;
            default -> value;
        };
    }

    public static void requireSupported(String contentType, byte[] bytes) {
        String normalized = normalizeContentType(contentType);
        if (normalized == null || !SUPPORTED.contains(normalized)) {
            throw new IllegalArgumentException("Image must be a JPEG, PNG, or WebP file");
        }
        if (!hasMatchingSignature(normalized, bytes)) {
            throw new IllegalArgumentException("Image content does not match its declared type");
        }
    }

    public static boolean hasMatchingSignature(String contentType, byte[] bytes) {
        if (bytes == null) {
            return false;
        }
        String normalized = normalizeContentType(contentType);
        return switch (normalized == null ? "" : normalized) {
            case JPEG -> bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff;
            case PNG -> bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89
                    && bytes[1] == 0x50
                    && bytes[2] == 0x4e
                    && bytes[3] == 0x47
                    && bytes[4] == 0x0d
                    && bytes[5] == 0x0a
                    && bytes[6] == 0x1a
                    && bytes[7] == 0x0a;
            case WEBP -> bytes.length >= 12
                    && bytes[0] == 'R'
                    && bytes[1] == 'I'
                    && bytes[2] == 'F'
                    && bytes[3] == 'F'
                    && bytes[8] == 'W'
                    && bytes[9] == 'E'
                    && bytes[10] == 'B'
                    && bytes[11] == 'P';
            default -> false;
        };
    }

    /**
     * Detects a supported format from the bytes, ignoring the declared type.
     * Returns null when the bytes are not a supported image.
     */
    public static String detectContentType(byte[] bytes) {
        if (hasMatchingSignature(JPEG, bytes)) {
            return JPEG;
        }
        if (hasMatchingSignature(PNG, bytes)) {
            return PNG;
        }
        if (hasMatchingSignature(WEBP, bytes)) {
            return WEBP;
        }
        return null;
    }

    public static String extension(String contentType) {
        String normalized = normalizeContentType(contentType);
        return EXTENSIONS.getOrDefault(normalized, "bin");
    }
}
