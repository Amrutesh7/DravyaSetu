package com.dravya.setu.common;

import com.dravya.setu.ai.AiServiceException;
import com.dravya.setu.common.ClientErrorException;
import com.dravya.setu.knowledge.KnowledgeServiceException;
import com.dravya.setu.knowledge.VoiceNotSupportedException;
import com.dravya.setu.mail.EmailDeliveryException;
import com.dravya.setu.storage.StorageException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex,
            HttpServletRequest request){

        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Invalid email or password",
                request
        );
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ErrorResponse> handleEmailNotVerified(
            EmailNotVerifiedException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.FORBIDDEN,
                "EMAIL_NOT_VERIFIED",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<ErrorResponse> handleEmailDeliveryFailure(
            EmailDeliveryException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "EMAIL_DELIVERY_FAILED",
                "The email could not be sent. Please try again shortly.",
                request);
    }

    @ExceptionHandler(ClientErrorException.class)
    public ResponseEntity<ErrorResponse> handleClientError(
            ClientErrorException ex,
            HttpServletRequest request) {

        HttpStatus status = switch (ex.getErrorCode()) {
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "CONFLICT" -> HttpStatus.CONFLICT;
            case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };

        return buildResponse(
                status,
                ex.getErrorCode(),
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        log.debug("IllegalArgumentException caught (generic): {}", ex.getMessage());
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Invalid request",
                request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.FORBIDDEN,
                "AUTH_FORBIDDEN",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<ErrorResponse> handleAiServiceError(
            AiServiceException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_SERVICE_UNAVAILABLE",
                "AI service is temporarily unavailable",
                request
        );
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorageError(
            StorageException ex,
            HttpServletRequest request) {

        log.error("Storage operation failed", ex);
        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "STORAGE_UNAVAILABLE",
                "Image storage is temporarily unavailable",
                request
        );
    }

    @ExceptionHandler(KnowledgeServiceException.class)
    public ResponseEntity<ErrorResponse> handleKnowledgeServiceError(
            KnowledgeServiceException ex,
            HttpServletRequest request) {

        log.warn("Knowledge request failed: request_id={} reason={}",
                request.getAttribute(RequestIdFilter.REQUEST_ID_HEADER), ex.getMessage());
        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "KNOWLEDGE_SERVICE_UNAVAILABLE",
                "Knowledge service is temporarily unavailable",
                request
        );
    }

    @ExceptionHandler(VoiceNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleVoiceNotSupported(
            VoiceNotSupportedException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.NOT_IMPLEMENTED,
                "VOICE_INPUT_UNSUPPORTED",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null){
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String code = switch (status) {
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case UNAUTHORIZED -> "AUTH_REQUIRED";
            case FORBIDDEN -> "AUTH_FORBIDDEN";
            case PAYLOAD_TOO_LARGE, CONTENT_TOO_LARGE -> "UPLOAD_TOO_LARGE";
            case TOO_MANY_REQUESTS -> "RATE_LIMITED";
            default -> status.is4xxClientError() ? "INVALID_REQUEST" : "INTERNAL_ERROR";
        };
        return buildResponse(status, code,
                status.is5xxServerError() ? "An unexpected error occurred"
                        : ex.getReason() == null ? status.getReasonPhrase() : ex.getReason(), request);
    }

    @ExceptionHandler({
            MissingServletRequestPartException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(
            Exception ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "The request could not be processed",
                request
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "UPLOAD_TOO_LARGE",
                "The uploaded file exceeds the allowed size",
                request
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataConflict(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "CONFLICT",
                "The request conflicts with existing data",
                request
        );
    }

    @ExceptionHandler({
            org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotAcceptableException.class,
            org.springframework.web.bind.ServletRequestBindingException.class
    })
    public ResponseEntity<ErrorResponse> handleWebError(Exception ex, HttpServletRequest request) {
        var error = (org.springframework.web.ErrorResponse) ex;
        HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
        var response = buildResponse(status, status == HttpStatus.NOT_FOUND ? "NOT_FOUND" : "INVALID_REQUEST",
                status.getReasonPhrase(), request);
        return ResponseEntity.status(status).headers(error.getHeaders()).body(response.getBody());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedError(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unhandled exception", ex);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {

        Object requestId =
                request.getAttribute(RequestIdFilter.REQUEST_ID_HEADER);

        String requestIdValue =
                requestId != null
                        ? requestId.toString()
                        : null;

        return ResponseEntity
                .status(status)
                .body(new ErrorResponse(
                        code,
                        message,
                        requestIdValue
                ));
    }
}
