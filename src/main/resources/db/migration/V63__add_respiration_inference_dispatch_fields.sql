-- Worker dispatch metadata only. No automatic processing is enabled by this migration.
ALTER TABLE respiration_records
    ADD COLUMN celery_task_id UUID,
    ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN processing_started_at TIMESTAMPTZ;

ALTER TABLE respiration_records
    ADD CONSTRAINT chk_respiration_retry_count_nonnegative CHECK (retry_count >= 0);

CREATE UNIQUE INDEX uq_respiration_records_celery_task_id
    ON respiration_records(celery_task_id)
    WHERE celery_task_id IS NOT NULL;

CREATE INDEX idx_respiration_records_processing_started_at
    ON respiration_records(status, processing_started_at);
