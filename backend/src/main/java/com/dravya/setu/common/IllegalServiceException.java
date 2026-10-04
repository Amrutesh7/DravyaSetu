package com.dravya.setu.common;

public class IllegalServiceException extends RuntimeException {

    public IllegalServiceException(String message) {
        super(message);
    }

    public IllegalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}