-- Destination pin used for automatic route calculation (both set or both null).
ALTER TABLE permit_areas
    ADD COLUMN latitude  NUMERIC(9, 6),
    ADD COLUMN longitude NUMERIC(9, 6);

ALTER TABLE permit_areas
    ADD CONSTRAINT ck_permit_areas_location
        CHECK ((latitude IS NULL) = (longitude IS NULL)
            AND (latitude IS NULL OR latitude BETWEEN -90 AND 90)
            AND (longitude IS NULL OR longitude BETWEEN -180 AND 180));
