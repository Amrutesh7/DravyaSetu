package com.dravya.setu.storage;

public class NoOpStorageService implements StorageService {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String upload(String key, byte[] content, String contentType) {
        throw new StorageException("Cloud storage is not configured");
    }

    @Override
    public String displayUrl(String key) {
        return null;
    }

    @Override
    public String aiUrl(String key) {
        return null;
    }

    @Override
    public void delete(String key) {
        // Nothing to delete when storage is disabled.
    }
}
