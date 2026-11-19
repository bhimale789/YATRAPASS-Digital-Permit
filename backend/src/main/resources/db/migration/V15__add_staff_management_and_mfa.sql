-- Staff account management and two-factor authentication (TOTP).
ALTER TABLE users
    ADD COLUMN display_name         VARCHAR(100),
    ADD COLUMN must_change_password BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN last_login_at        TIMESTAMPTZ,
    -- AES-256-GCM ciphertext of the base32 TOTP secret (bound to the user id); never plaintext.
    ADD COLUMN totp_secret          VARCHAR(255),
    ADD COLUMN totp_enabled         BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Last accepted 30-second time step, so a code cannot be replayed.
    ADD COLUMN totp_last_step       BIGINT;

ALTER TABLE users
    ADD CONSTRAINT ck_users_totp_enabled_has_secret CHECK (NOT totp_enabled OR totp_secret IS NOT NULL);

-- One-time recovery codes for when the authenticator device is lost (SHA-256 hashes only).
CREATE TABLE mfa_recovery_codes (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    code_hash  VARCHAR(64) NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_mfa_recovery_codes_hash UNIQUE (code_hash),
    CONSTRAINT fk_mfa_recovery_codes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_mfa_recovery_codes_user ON mfa_recovery_codes (user_id);
