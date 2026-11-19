-- Passport/identity fields are nullable at registration and must be completed
-- before a tourist may submit a permit application (enforced in the service layer).
CREATE TABLE tourist_profiles (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    passport_number   VARCHAR(20),
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    nationality       VARCHAR(100),
    date_of_birth     DATE,
    gender            VARCHAR(20),
    phone             VARCHAR(30),
    address           VARCHAR(500),
    emergency_contact VARCHAR(255),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_tourist_profiles_user UNIQUE (user_id),
    CONSTRAINT uk_tourist_profiles_passport UNIQUE (passport_number),
    CONSTRAINT fk_tourist_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_tourist_profiles_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER', 'UNDISCLOSED'))
);

CREATE INDEX idx_tourist_profiles_name ON tourist_profiles (lower(last_name), lower(first_name));
