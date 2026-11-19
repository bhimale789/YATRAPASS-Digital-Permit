package com.example.touristpermit.security;

import com.example.touristpermit.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** The shared (database-backed) rate limiter, as used by every backend instance. */
class RateLimiterIT extends AbstractIntegrationTest {

    static class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-11-01T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager txManager;

    MutableClock clock;
    RateLimiter limiter;
    String key;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        limiter = new RateLimiter(jdbc, txManager, clock);
        key = "test:" + UUID.randomUUID();
    }

    @Test
    void blocksAfterMaxHitsAndResetsAfterWindow() {
        Duration window = Duration.ofMinutes(5);
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.isBlocked(key, 3, window)).isFalse();
            limiter.hit(key, window);
        }
        assertThat(limiter.isBlocked(key, 3, window)).isTrue();

        clock.now = clock.now.plus(Duration.ofMinutes(6));
        assertThat(limiter.isBlocked(key, 3, window)).isFalse();
        limiter.hit(key, window);
        assertThat(jdbc.queryForObject("SELECT hits FROM rate_limit_buckets WHERE window_start = ?",
                Integer.class, java.sql.Timestamp.from(clock.now))).isEqualTo(1);
    }

    @Test
    void resetClearsTheCounter() {
        Duration window = Duration.ofMinutes(5);
        limiter.hit(key, window);
        limiter.hit(key, window);
        limiter.reset(key);
        assertThat(limiter.isBlocked(key, 1, window)).isFalse();
    }

    @Test
    void hitsSurviveCallerRollback() {
        Duration window = Duration.ofMinutes(5);
        TransactionTemplate outer = new TransactionTemplate(txManager);
        outer.executeWithoutResult(status -> {
            limiter.hit(key, window);
            status.setRollbackOnly(); // e.g. a failed login rolls back its own transaction
        });
        assertThat(limiter.isBlocked(key, 1, window)).isTrue();
    }

    @Test
    void concurrentHitsFromSeveralInstancesAreAllCounted() throws Exception {
        Duration window = Duration.ofMinutes(5);
        // Separate limiter objects simulate separate backend instances sharing one database.
        List<RateLimiter> instances = List.of(new RateLimiter(jdbc, txManager, clock),
                new RateLimiter(jdbc, txManager, clock), new RateLimiter(jdbc, txManager, clock));
        try (var pool = Executors.newFixedThreadPool(6)) {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 30; i++) {
                RateLimiter instance = instances.get(i % instances.size());
                futures.add(pool.submit(() -> instance.hit(key, window)));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        }
        assertThat(limiter.isBlocked(key, 30, window)).isTrue();
        assertThat(limiter.isBlocked(key, 31, window)).isFalse();
    }

    @Test
    void rawKeysAreNotStored() {
        limiter.hit("login-acct:someone@example.com", Duration.ofMinutes(5));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rate_limit_buckets WHERE bucket_key LIKE '%@%'",
                Long.class)).isZero();
    }
}
