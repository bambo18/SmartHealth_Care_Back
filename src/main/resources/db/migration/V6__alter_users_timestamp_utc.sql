ALTER TABLE users
ALTER COLUMN login_attempt_started_at TYPE TIMESTAMP WITH TIME ZONE
USING login_attempt_started_at AT TIME ZONE 'UTC';

ALTER TABLE users
ALTER COLUMN password_reset_token_expiry TYPE TIMESTAMP WITH TIME ZONE
USING password_reset_token_expiry AT TIME ZONE 'UTC';

ALTER TABLE users
ALTER COLUMN password_reset_requested_at TYPE TIMESTAMP WITH TIME ZONE
USING password_reset_requested_at AT TIME ZONE 'UTC';

ALTER TABLE users
ALTER COLUMN email_verification_expiry TYPE TIMESTAMP WITH TIME ZONE
USING email_verification_expiry AT TIME ZONE 'UTC';

ALTER TABLE users
ALTER COLUMN email_verification_requested_at TYPE TIMESTAMP WITH TIME ZONE
USING email_verification_requested_at AT TIME ZONE 'UTC';

ALTER TABLE users
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
USING created_at AT TIME ZONE 'UTC';

ALTER TABLE users
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE
USING updated_at AT TIME ZONE 'UTC';
