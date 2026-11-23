package com.dravya.setu.common;

import org.springframework.http.HttpStatus;

/**
 * Signals that the credentials were correct but the account has not verified its email yet.
 * The HTTP status is 403 rather than 401 so clients can send the user to the verification
 * screen instead of asking them to re-enter the password.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException(String message) {
        super(message);
    }

    public HttpStatus status() {
        return HttpStatus.FORBIDDEN;
    }
}
