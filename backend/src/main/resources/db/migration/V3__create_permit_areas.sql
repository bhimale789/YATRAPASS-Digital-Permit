-- No real-world areas, rules or fees are seeded here. Development sample data
-- is inserted by the application only when the "dev" profile is active.
CREATE TABLE permit_areas (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(150)   NOT NULL,
    code            VARCHAR(30)    NOT NULL,
    description     VARCHAR(2000),
    district        VARCHAR(100),
    province        VARCHAR(100),
    permit_required BOOLEAN        NOT NULL DEFAULT TRUE,
    maximum_days    INTEGER        NOT NULL,
    fee             NUMERIC(12, 2) NOT NULL DEFAULT 0,
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_permit_areas_code UNIQUE (code),
    CONSTRAINT ck_permit_areas_max_days CHECK (maximum_days > 0),
    CONSTRAINT ck_permit_areas_fee CHECK (fee >= 0)
);

CREATE INDEX idx_permit_areas_active ON permit_areas (active);
