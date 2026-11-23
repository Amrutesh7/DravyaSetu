package com.dravya.setu.storage;

/**
 * Deterministic object-key layout. A per-identification random storage id keeps
 * keys opaque and prevents user-supplied idempotency keys from reaching paths.
 */
public final class ObjectKeys {

    private ObjectKeys() {
    }

    public static String imageKey(
            Long userId,
            String storageId,
            int sequenceNumber,
            String contentType) {
        return "identifications/" + userId + "/" + storageId
                + "/image-" + sequenceNumber + "." + ImageContent.extension(contentType);
    }

    public static String heatmapKey(
            Long userId,
            String storageId,
            int imageSequenceNumber,
            int heatmapSequenceNumber,
            String contentType) {
        return "identifications/" + userId + "/" + storageId
                + "/heatmap-" + imageSequenceNumber + "-" + heatmapSequenceNumber
                + "." + ImageContent.extension(contentType);
    }
}
