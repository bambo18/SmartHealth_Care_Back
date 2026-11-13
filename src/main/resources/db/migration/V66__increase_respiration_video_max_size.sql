-- V62 created 30 MiB CHECK constraints on both size columns.
-- DO NOT modify already applied V62/V63 migrations.
-- This changes the maximum to 200 MiB (209,715,200 bytes), matching the
-- upload request validation and S3 HEAD validation.

ALTER TABLE respiration_records
    DROP CONSTRAINT IF EXISTS respiration_records_expected_size_bytes_check,
    DROP CONSTRAINT IF EXISTS respiration_records_actual_size_bytes_check;

ALTER TABLE respiration_records
    ADD CONSTRAINT chk_respiration_expected_size_bytes_200mib
        CHECK (expected_size_bytes > 0 AND expected_size_bytes <= 209715200),
    ADD CONSTRAINT chk_respiration_actual_size_bytes_200mib
        CHECK (actual_size_bytes IS NULL OR
               (actual_size_bytes > 0 AND actual_size_bytes <= 209715200));
