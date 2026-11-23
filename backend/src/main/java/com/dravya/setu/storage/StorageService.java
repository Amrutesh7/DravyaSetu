package com.dravya.setu.storage;

/**
 * Stores binary objects in B2 and produces short-lived signed GET URLs.
 */
public interface StorageService {

    boolean isEnabled();

    /**
     * Uploads an object and returns its key.
     */
    String upload(String key, byte[] content, String contentType);

    /**
     * Signed URL for web/mobile display.
     */
    String displayUrl(String key);

    /**
     * Shorter signed URL handed to the internal AI service.
     */
    String aiUrl(String key);

    /**
     * Best-effort deletion used to clean up orphaned objects.
     */
    void delete(String key);
}
