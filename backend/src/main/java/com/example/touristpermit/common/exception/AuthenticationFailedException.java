package com.example.touristpermit.common.exception;

import org.springframework.http.HttpStatus;

public class AuthenticationFailedException extends ApiException {

    public AuthenticationFailedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
