package com.example.touristpermit.route;

import com.example.touristpermit.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/** The external routing service failed or timed out (HTTP 503). */
public class RouteUnavailableException extends ApiException {

    public RouteUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "The route service is temporarily unavailable. Please try again later.");
    }
}
