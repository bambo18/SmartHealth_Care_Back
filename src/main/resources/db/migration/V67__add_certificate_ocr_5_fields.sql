-- VERIFY migration version before copying! V65 is only a candidate.
-- New optional certificate fields (existing rows remain valid).
ALTER TABLE pet_health_certificates
    ADD COLUMN IF NOT EXISTS registration_number VARCHAR(32),
    ADD COLUMN IF NOT EXISTS symptoms TEXT,
    ADD COLUMN IF NOT EXISTS treatment TEXT,
    ADD COLUMN IF NOT EXISTS hospitalization_discharge_date_text VARCHAR(128),
    ADD COLUMN IF NOT EXISTS document_date_text VARCHAR(64);
