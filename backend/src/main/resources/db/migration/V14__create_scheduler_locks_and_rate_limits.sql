-- ShedLock: ensures each scheduled job runs on only one backend instance at a time.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);

-- Fixed-window counters shared by all backend instances (login / 2FA attempt limiting).
-- bucket_key is a SHA-256 of the limited subject (IP or account), never the raw value.
CREATE TABLE rate_limit_buckets (
    bucket_key   VARCHAR(64)  NOT NULL,
    window_start TIMESTAMPTZ  NOT NULL,
    hits         INTEGER      NOT NULL,
    CONSTRAINT pk_rate_limit_buckets PRIMARY KEY (bucket_key),
    CONSTRAINT ck_rate_limit_buckets_hits CHECK (hits > 0)
);

CREATE INDEX idx_rate_limit_buckets_window ON rate_limit_buckets (window_start);
