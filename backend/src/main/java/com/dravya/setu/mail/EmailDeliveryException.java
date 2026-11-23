package com.dravya.setu.mail;

/**
 * Email could not be delivered. The API surfaces this as {@code 503 EMAIL_DELIVERY_FAILED}.
 * Messages intentionally contain no credentials, recipients or message bodies.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
