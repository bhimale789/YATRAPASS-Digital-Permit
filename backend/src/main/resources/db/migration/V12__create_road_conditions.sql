-- Road conditions maintained by administrators and shown on maps to tourists/officers.
-- coordinates: JSON array of {"lat": .., "lng": ..}; 1 point for POINT, 2+ for LINE.
CREATE TABLE road_conditions (
    id            BIGSERIAL PRIMARY KEY,
    area_id       BIGINT,
    title         VARCHAR(150)  NOT NULL,
    description   VARCHAR(1000),
    status        VARCHAR(30)   NOT NULL,
    geometry_type VARCHAR(10)   NOT NULL,
    coordinates   JSONB         NOT NULL,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by    BIGINT,
    updated_by    BIGINT,
    version       BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_road_conditions_area FOREIGN KEY (area_id) REFERENCES permit_areas (id) ON DELETE SET NULL,
    CONSTRAINT fk_road_conditions_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_road_conditions_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_road_conditions_status CHECK (status IN ('OPEN', 'CAUTION', 'PARTIALLY_BLOCKED', 'CLOSED')),
    CONSTRAINT ck_road_conditions_geometry CHECK (geometry_type IN ('POINT', 'LINE')),
    CONSTRAINT ck_road_conditions_coordinates CHECK (jsonb_typeof(coordinates) = 'array')
);

CREATE INDEX idx_road_conditions_active ON road_conditions (active, updated_at DESC);
CREATE INDEX idx_road_conditions_area ON road_conditions (area_id);
