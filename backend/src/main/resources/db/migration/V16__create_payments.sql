-- Permit fee captured when the application is submitted, so later fee changes do not affect it.
ALTER TABLE permit_applications
    ADD COLUMN fee_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;

UPDATE permit_applications app
SET fee_amount = area.fee
FROM permit_areas area
WHERE area.id = app.area_id;

ALTER TABLE permit_applications
    ADD CONSTRAINT ck_permit_applications_fee CHECK (fee_amount >= 0);

-- Permit fee payments (eSewa online, or recorded offline by an administrator).
CREATE TABLE payments (
    id               BIGSERIAL PRIMARY KEY,
    application_id   BIGINT         NOT NULL,
    provider         VARCHAR(20)    NOT NULL,
    amount           NUMERIC(12, 2) NOT NULL,
    currency         VARCHAR(3)     NOT NULL DEFAULT 'NPR',
    status           VARCHAR(20)    NOT NULL,
    -- Our unique reference sent to the gateway (random, unguessable).
    transaction_uuid VARCHAR(64)    NOT NULL,
    -- Gateway reference (eSewa transaction_code / ref_id, or an offline receipt number).
    provider_ref     VARCHAR(100),
    failure_reason   VARCHAR(255),
    recorded_by      BIGINT,
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    completed_at     TIMESTAMPTZ,
    CONSTRAINT uk_payments_transaction_uuid UNIQUE (transaction_uuid),
    CONSTRAINT fk_payments_application FOREIGN KEY (application_id) REFERENCES permit_applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_payments_recorded_by FOREIGN KEY (recorded_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_payments_provider CHECK (provider IN ('ESEWA', 'OFFLINE')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_completed CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL))
);

-- An application can be paid only once.
CREATE UNIQUE INDEX uk_payments_one_completed_per_application
    ON payments (application_id) WHERE status = 'COMPLETED';
CREATE INDEX idx_payments_application ON payments (application_id);
CREATE INDEX idx_payments_status_created ON payments (status, created_at);
