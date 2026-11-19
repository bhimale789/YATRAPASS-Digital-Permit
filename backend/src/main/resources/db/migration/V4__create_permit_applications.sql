CREATE SEQUENCE application_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE permit_applications (
    id                   BIGSERIAL PRIMARY KEY,
    application_number   VARCHAR(30)  NOT NULL,
    tourist_id           BIGINT       NOT NULL,
    area_id              BIGINT       NOT NULL,
    purpose              VARCHAR(1000) NOT NULL,
    requested_start_date DATE         NOT NULL,
    requested_end_date   DATE         NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    rejection_reason     VARCHAR(1000),
    submitted_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reviewed_at          TIMESTAMPTZ,
    reviewed_by          BIGINT,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_permit_applications_number UNIQUE (application_number),
    CONSTRAINT fk_permit_applications_tourist FOREIGN KEY (tourist_id) REFERENCES tourist_profiles (id),
    CONSTRAINT fk_permit_applications_area FOREIGN KEY (area_id) REFERENCES permit_areas (id),
    CONSTRAINT fk_permit_applications_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id),
    CONSTRAINT ck_permit_applications_status CHECK (status IN ('PENDING', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_permit_applications_dates CHECK (requested_end_date >= requested_start_date)
);

CREATE INDEX idx_permit_applications_tourist ON permit_applications (tourist_id);
CREATE INDEX idx_permit_applications_area ON permit_applications (area_id);
CREATE INDEX idx_permit_applications_status ON permit_applications (status, submitted_at DESC);
