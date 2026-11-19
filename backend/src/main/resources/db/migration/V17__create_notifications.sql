-- In-app notifications plus an outbox of email/SMS deliveries. Rows are written in the
-- same transaction as the event (approval, rejection, payment) and sent by a scheduled
-- dispatcher with retries. Recipient addresses are looked up at send time and are not
-- copied here.
CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(40)   NOT NULL
        CHECK (type IN ('APPLICATION_APPROVED', 'APPLICATION_REJECTED', 'PERMIT_EXPIRING', 'PAYMENT_RECEIVED')),
    title       VARCHAR(200)  NOT NULL,
    body        VARCHAR(2000) NOT NULL,
    link        VARCHAR(300),
    -- Prevents duplicates when a job runs twice (e.g. PERMIT_EXPIRING:42:3)
    dedup_key   VARCHAR(150)  NOT NULL UNIQUE,
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL;

CREATE TABLE notification_deliveries (
    id               BIGSERIAL PRIMARY KEY,
    notification_id  BIGINT       NOT NULL REFERENCES notifications (id) ON DELETE CASCADE,
    channel          VARCHAR(10)  NOT NULL CHECK (channel IN ('EMAIL', 'SMS')),
    status           VARCHAR(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    attempts         INT          NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error       VARCHAR(300),
    next_attempt_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (notification_id, channel)
);

CREATE INDEX idx_deliveries_due ON notification_deliveries (next_attempt_at) WHERE status = 'PENDING';
