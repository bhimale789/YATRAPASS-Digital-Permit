CREATE SEQUENCE permit_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE permits (
    id                  BIGSERIAL PRIMARY KEY,
    permit_number       VARCHAR(30)  NOT NULL,
    application_id      BIGINT       NOT NULL,
    tourist_id          BIGINT       NOT NULL,
    area_id             BIGINT       NOT NULL,
    issue_date          DATE         NOT NULL,
    valid_from          DATE         NOT NULL,
    valid_until         DATE         NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    issued_by           BIGINT       NOT NULL,
    cancellation_reason VARCHAR(1000),
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_permits_number UNIQUE (permit_number),
    CONSTRAINT uk_permits_application UNIQUE (application_id),
    CONSTRAINT fk_permits_application FOREIGN KEY (application_id) REFERENCES permit_applications (id),
    CONSTRAINT fk_permits_tourist FOREIGN KEY (tourist_id) REFERENCES tourist_profiles (id),
    CONSTRAINT fk_permits_area FOREIGN KEY (area_id) REFERENCES permit_areas (id),
    CONSTRAINT fk_permits_issuer FOREIGN KEY (issued_by) REFERENCES users (id),
    CONSTRAINT ck_permits_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_permits_dates CHECK (valid_until >= valid_from)
);

CREATE INDEX idx_permits_tourist ON permits (tourist_id);
CREATE INDEX idx_permits_area ON permits (area_id);
CREATE INDEX idx_permits_status_until ON permits (status, valid_until);
