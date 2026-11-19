-- Optional destination photo managed by administrators. The file lives in file storage;
-- only its randomized relative path and metadata are stored here.
ALTER TABLE permit_areas
    ADD COLUMN photo_path       VARCHAR(500),
    ADD COLUMN photo_mime_type  VARCHAR(100),
    ADD COLUMN photo_updated_at TIMESTAMPTZ;

ALTER TABLE permit_areas
    ADD CONSTRAINT uk_permit_areas_photo_path UNIQUE (photo_path),
    ADD CONSTRAINT ck_permit_areas_photo_complete
        CHECK ((photo_path IS NULL) = (photo_mime_type IS NULL) AND (photo_path IS NULL) = (photo_updated_at IS NULL));
