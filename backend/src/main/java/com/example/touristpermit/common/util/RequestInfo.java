package com.example.touristpermit.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Client metadata for audit/verification logs. The remote address is taken from the
 * servlet container, which honours X-Forwarded-For only when
 * {@code server.forward-headers-strategy} is configured for a trusted proxy.
 */
public record RequestInfo(String ipAddress, String userAgent) {

    private static final int MAX_USER_AGENT = 500;

    public static RequestInfo from(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua != null && ua.length() > MAX_USER_AGENT) {
            ua = ua.substring(0, MAX_USER_AGENT);
        }
        return new RequestInfo(request.getRemoteAddr(), ua);
    }

    /** Request info for the current thread, or empty values outside a web request (e.g. scheduled jobs). */
    public static RequestInfo current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return from(attrs.getRequest());
        }
        return new RequestInfo(null, null);
    }
}
