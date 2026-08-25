-- Step 1: Drop the existing constraint
ALTER TABLE submissions
    DROP CONSTRAINT check_submission_status_values;

-- Step 2: Add the new constraint with 'DELETED' included
ALTER TABLE submissions
    ADD CONSTRAINT check_submission_status_values
    CHECK (status IN (
        'PENDING',
        'PROCESSING',
        'COMPLETED',
        'FAILED',
        'DELETED',
        'CANCELED' -- This is the new value
    ));