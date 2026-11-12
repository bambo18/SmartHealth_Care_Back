ALTER TABLE shelters
    ADD COLUMN province VARCHAR(50),
    ADD COLUMN district VARCHAR(50),
    ADD COLUMN facility_type VARCHAR(50),
    ADD COLUMN jurisdiction VARCHAR(255),
    ADD COLUMN is_outside_district BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN source VARCHAR(255),
    ADD COLUMN source_checked_at DATE;

CREATE INDEX idx_shelters_province_district
    ON shelters (province, district);

CREATE INDEX idx_shelters_jurisdiction
    ON shelters (jurisdiction);

CREATE INDEX idx_shelters_active
    ON shelters (is_active);
