package com.example.touristpermit.common.exception;

import org.springframework.http.HttpStatus;

/** Syntactically valid request that violates a business rule (HTTP 422). */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
