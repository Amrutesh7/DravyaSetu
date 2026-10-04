package com.dravya.setu.common;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

public class ErrorResponse {

    private ErrorBody error;

    public ErrorResponse() {
    }

    public ErrorResponse(
            String code,
            String message,
            String requestId) {

        this.error = new ErrorBody(
                code,
                message,
                requestId
        );
    }

    public ErrorBody getError() {
        return error;
    }

    public void setError(ErrorBody error) {
        this.error = error;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ErrorBody {

        private String code;
        private String message;
        private String requestId;

        public ErrorBody() {
        }

        public ErrorBody(
                String code,
                String message,
                String requestId) {

            this.code = code;
            this.message = message;
            this.requestId = requestId;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public String getRequestId() {
            return requestId;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }
    }
}