package com.example.touristpermit.security;

import com.example.touristpermit.config.AppProperties;
import com.example.touristpermit.support.TestProperties;
import com.example.touristpermit.user.Role;
import com.example.touristpermit.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    static final String SECRET = TestProperties.JWT_SECRET;

    static AppProperties props(String secret) {
        return TestProperties.withJwtSecret(secret);
    }

    static User user(long id) {
        User u = new User("a@b.c", "hash", Role.ADMIN);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    void roundTripsUserId() {
        JwtService jwt = new JwtService(props(SECRET), Clock.systemUTC());
        assertThat(jwt.parseUserId(jwt.issueAccessToken(user(42)))).contains(42L);
    }

    @Test
    void rejectsExpiredToken() {
        Instant issued = Instant.parse("2026-01-01T00:00:00Z");
        String token = new JwtService(props(SECRET), Clock.fixed(issued, ZoneOffset.UTC)).issueAccessToken(user(1));
        JwtService later = new JwtService(props(SECRET), Clock.fixed(issued.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));
        assertThat(later.parseUserId(token)).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        String other = Base64.getEncoder().encodeToString("ffffffffffffffffffffffffffffffff".getBytes());
        String token = new JwtService(props(other), Clock.systemUTC()).issueAccessToken(user(1));
        assertThat(new JwtService(props(SECRET), Clock.systemUTC()).parseUserId(token)).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        assertThat(new JwtService(props(SECRET), Clock.systemUTC()).parseUserId("not.a.jwt")).isEmpty();
    }

    @Test
    void refusesShortSecret() {
        String shortKey = Base64.getEncoder().encodeToString("short".getBytes());
        assertThatThrownBy(() -> new JwtService(props(shortKey), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }
}
