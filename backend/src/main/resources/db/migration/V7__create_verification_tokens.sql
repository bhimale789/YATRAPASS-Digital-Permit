-- QR verification tokens.
--   token_hash       : SHA-256 (hex) of the raw token; used for lookup during verification.
--   token_ciphertext : AES-GCM encryption of the raw token (key from QR_TOKEN_ENCRYPTION_KEY),
--                      so the permit holder's QR image can be re-rendered without storing
--                      the plaintext token. A database dump alone cannot forge/replay QR codes.
CREATE TABLE verification_tokens (
    id                BIGSERIAL PRIMARY KEY,
    permit_id         BIGINT       NOT NULL,
    token_hash        VARCHAR(64)  NOT NULL,
    token_ciphertext  VARCHAR(255) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        BIGINT,
    revoked_at        TIMESTAMPTZ,
    revoked_by        BIGINT,
    revocation_reason VARCHAR(500),
    CONSTRAINT uk_verification_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_verification_tokens_permit FOREIGN KEY (permit_id) REFERENCES permits (id) ON DELETE CASCADE,
    CONSTRAINT fk_verification_tokens_creator FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_verification_tokens_revoker FOREIGN KEY (revoked_by) REFERENCES users (id),
    CONSTRAINT ck_verification_tokens_status CHECK (status IN ('ACTIVE', 'REVOKED'))
);

-- At most one ACTIVE token per permit.
CREATE UNIQUE INDEX uk_verification_tokens_active_per_permit
    ON verification_tokens (permit_id) WHERE status = 'ACTIVE';
