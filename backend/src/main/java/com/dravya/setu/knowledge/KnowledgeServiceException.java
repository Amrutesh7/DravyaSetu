package com.dravya.setu.knowledge;

public class KnowledgeServiceException extends RuntimeException {

    public KnowledgeServiceException(String message) {
        super(message);
    }

    public KnowledgeServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}