ALTER TABLE submissions
    ALTER COLUMN failure_reason TYPE VARCHAR(255);

ALTER TABLE submissions
    ADD COLUMN celery_task_string TEXT;