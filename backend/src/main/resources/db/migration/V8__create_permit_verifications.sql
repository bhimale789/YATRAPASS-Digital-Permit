-- permit_id is nullable: scans of unknown/forged tokens are logged too.
CREATE TABLE permit_verifications (
    id             BIGSERIAL PRIMARY KEY,
    permit_id      BIGINT,
    verified_by    BIGINT       NOT NULL,
    verified_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    result         VARCHAR(20)  NOT NULL,
    failure_reason VARCHAR(30),
    ip_address     VARCHAR(45),
    user_agent     VARCHAR(500),
    latitude       NUMERIC(9, 6),
    longitude      NUMERIC(9, 6),
    CONSTRAINT fk_permit_verifications_permit FOREIGN KEY (permit_id) REFERENCES permits (id) ON DELETE SET NULL,
    CONSTRAINT fk_permit_verifications_user FOREIGN KEY (verified_by) REFERENCES users (id),
    CONSTRAINT ck_permit_verifications_result CHECK (result IN ('VALID', 'INVALID', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_permit_verifications_lat CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_permit_verifications_lon CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_permit_verifications_at ON permit_verifications (verified_at DESC);
CREATE INDEX idx_permit_verifications_permit ON permit_verifications (permit_id);
CREATE INDEX idx_permit_verifications_result ON permit_verifications (result);
