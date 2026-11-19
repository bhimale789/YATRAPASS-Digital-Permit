package com.example.touristpermit.security;

import com.example.touristpermit.common.util.SecureTokens;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Fixed-window attempt counters stored in PostgreSQL, so limits are shared by every
 * backend instance. Writes run in their own transaction: a failed login rolls back its
 * own transaction, but the recorded failure must still count.
 * <p>
 * Keys are hashed before storage so raw IPs/emails are never persisted here.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public RateLimiter(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** True when the key has reached {@code max} hits within the current window. */
    public boolean isBlocked(String key, int max, Duration window) {
        Instant windowStart = clock.instant().minus(window);
        Integer hits = tx.execute(status -> jdbc.query(
                "SELECT hits FROM rate_limit_buckets WHERE bucket_key = ? AND window_start > ?",
                rs -> rs.next() ? rs.getInt(1) : 0,
                hash(key), Timestamp.from(windowStart)));
        return hits != null && hits >= max;
    }

    /** Records one hit; starts a new window when the previous one has elapsed. Atomic across instances. */
    public void hit(String key, Duration window) {
        Instant now = clock.instant();
        Timestamp nowTs = Timestamp.from(now);
        Timestamp expiredBefore = Timestamp.from(now.minus(window));
        tx.executeWithoutResult(status -> jdbc.update("""
                INSERT INTO rate_limit_buckets (bucket_key, window_start, hits) VALUES (?, ?, 1)
                ON CONFLICT (bucket_key) DO UPDATE SET
                    hits = CASE WHEN rate_limit_buckets.window_start <= ? THEN 1 ELSE rate_limit_buckets.hits + 1 END,
                    window_start = CASE WHEN rate_limit_buckets.window_start <= ? THEN EXCLUDED.window_start
                                        ELSE rate_limit_buckets.window_start END
                """, hash(key), nowTs, expiredBefore, expiredBefore));
    }

    public void reset(String key) {
        tx.executeWithoutResult(status -> jdbc.update("DELETE FROM rate_limit_buckets WHERE bucket_key = ?", hash(key)));
    }

    /** Removes buckets older than a day (longest window in use is far shorter). */
    @Scheduled(cron = "0 15 * * * *")
    @SchedulerLock(name = "rateLimitCleanup")
    public void purgeOld() {
        int removed = jdbc.update("DELETE FROM rate_limit_buckets WHERE window_start < ?",
                Timestamp.from(clock.instant().minus(Duration.ofDays(1))));
        if (removed > 0) {
            log.debug("Purged {} rate-limit buckets", removed);
        }
    }

    private static String hash(String key) {
        return SecureTokens.sha256Hex(key);
    }
}
