package com.dravya.setu.common;

public class ClientErrorException extends RuntimeException {

    private final String errorCode;

    public ClientErrorException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public static ClientErrorException badRequest(String message) {
        return new ClientErrorException("INVALID_REQUEST", message);
    }

    public static ClientErrorException notFound(String message) {
        return new ClientErrorException("NOT_FOUND", message);
    }

    public static ClientErrorException conflict(String message) {
        return new ClientErrorException("CONFLICT", message);
    }
}