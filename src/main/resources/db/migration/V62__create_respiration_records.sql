-- Use V62 only if it is the next unused Flyway version in this repository.
-- The actual video is stored in OVH Object Storage, never in PostgreSQL.
CREATE TABLE respiration_records (
    id UUID PRIMARY KEY,
    pet_id BIGINT NOT NULL REFERENCES pets(id) ON DELETE CASCADE,
    storage_key VARCHAR(512) NOT NULL UNIQUE,
    expected_size_bytes BIGINT NOT NULL CHECK (expected_size_bytes > 0 AND expected_size_bytes <= 31457280),
    actual_size_bytes BIGINT CHECK (actual_size_bytes > 0 AND actual_size_bytes <= 31457280),
    duration_sec INTEGER NOT NULL CHECK (duration_sec BETWEEN 30 AND 120),
    status VARCHAR(32) NOT NULL DEFAULT 'UPLOADING'
        CHECK (status IN ('UPLOADING', 'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    respiratory_rate NUMERIC(6,2),
    failure_reason VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_respiration_records_pet_created_at
    ON respiration_records(pet_id, created_at DESC);

CREATE INDEX idx_respiration_records_status_created_at
    ON respiration_records(status, created_at);
