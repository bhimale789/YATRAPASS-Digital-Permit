-- File contents live in configurable file storage; only metadata is stored here.
CREATE TABLE permit_documents (
    id             BIGSERIAL PRIMARY KEY,
    application_id BIGINT       NOT NULL,
    document_type  VARCHAR(30)  NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    storage_path   VARCHAR(500) NOT NULL,
    mime_type      VARCHAR(100) NOT NULL,
    file_size      BIGINT       NOT NULL,
    uploaded_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    verified       BOOLEAN      NOT NULL DEFAULT FALSE,
    verified_at    TIMESTAMPTZ,
    verified_by    BIGINT,
    CONSTRAINT uk_permit_documents_storage_path UNIQUE (storage_path),
    CONSTRAINT fk_permit_documents_application FOREIGN KEY (application_id) REFERENCES permit_applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_permit_documents_verifier FOREIGN KEY (verified_by) REFERENCES users (id),
    CONSTRAINT ck_permit_documents_type CHECK (document_type IN ('PASSPORT', 'VISA', 'PHOTO', 'TRAVEL_INSURANCE', 'ITINERARY', 'OTHER')),
    CONSTRAINT ck_permit_documents_size CHECK (file_size > 0)
);

CREATE INDEX idx_permit_documents_application ON permit_documents (application_id);
