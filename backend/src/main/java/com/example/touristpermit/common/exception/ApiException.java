package com.example.touristpermit.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base for all expected, client-facing errors. The message is returned to the client,
 * so it must never contain sensitive data.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
