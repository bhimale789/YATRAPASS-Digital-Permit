package com.example.touristpermit.security;

import com.example.touristpermit.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Failed-login limiting keyed by client IP and by account. Counters are stored in the
 * database via {@link RateLimiter}, so the limit holds across multiple backend instances.
 */
@Component
public class LoginRateLimiter {

    private final RateLimiter limiter;
    private final int maxAttempts;
    private final Duration window;

    public LoginRateLimiter(RateLimiter limiter, AppProperties properties) {
        this.limiter = limiter;
        this.maxAttempts = properties.security().loginRateLimit().maxAttempts();
        this.window = properties.security().loginRateLimit().window();
    }

    public boolean isBlocked(String ip, String email) {
        return limiter.isBlocked("login-ip:" + ip, maxAttempts, window)
                || limiter.isBlocked("login-acct:" + email, maxAttempts, window);
    }

    public void recordFailure(String ip, String email) {
        limiter.hit("login-ip:" + ip, window);
        limiter.hit("login-acct:" + email, window);
    }

    public void recordSuccess(String email) {
        limiter.reset("login-acct:" + email);
    }

    // ---- second factor (TOTP / recovery code) attempts, per account

    public boolean isMfaBlocked(long userId) {
        return limiter.isBlocked("mfa:" + userId, maxAttempts, window);
    }

    public void recordMfaFailure(long userId) {
        limiter.hit("mfa:" + userId, window);
    }

    public void recordMfaSuccess(long userId) {
        limiter.reset("mfa:" + userId);
    }
}
