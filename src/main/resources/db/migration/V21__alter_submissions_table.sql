-- V2__add_submission_status_check.sql
-- Adds status tracking and failure reason to the submissions table using a CHECK constraint.

-- 2. Add the new status column (VARCHAR with a CHECK constraint)
ALTER TABLE submissions
    ADD COLUMN status varchar(255) NOT NULL;

-- 3. Add the CHECK constraint to enforce valid status values
ALTER TABLE submissions
    ADD CONSTRAINT check_submission_status_values
    CHECK (status IN (
        'PENDING',
        'PROCESSING',
        'COMPLETED',
        'FAILED'
    ));

-- 4. Add the failure reason column (TEXT, nullable)
ALTER TABLE submissions
    ADD COLUMN failure_reason TEXT;
    
-- Note: If you have existing data, they will now default to PENDING.
-- If you need existing records that have a completed_at time to be 'COMPLETED',
-- you can run an update (optional):
/*
UPDATE submissions
SET status = 'COMPLETED'
WHERE completed_at IS NOT NULL
  AND status = 'PENDING';
*/