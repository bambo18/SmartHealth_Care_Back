-- Flyway/Liquibase convention suggests naming the file with a version prefix (e.g., V2__)

-- Alter the data type of the login_attempt column to INTEGER
ALTER TABLE users
ALTER COLUMN login_attempt TYPE INTEGER;

-- Alter the data type of the password_reset_token_verify_fail_count column to INTEGER
ALTER TABLE users
ALTER COLUMN password_reset_token_verify_fail_count TYPE INTEGER;

-- Alter the data type of the email_verification_fail_count column to INTEGER
ALTER TABLE users
ALTER COLUMN email_verification_fail_count TYPE INTEGER;

-- Optionally, you can also set the default value again,
-- although PostgreSQL often preserves it during a simple type change.
-- It's a good practice to be explicit if you want to enforce it.

ALTER TABLE users
ALTER COLUMN login_attempt SET DEFAULT 0;

ALTER TABLE users
ALTER COLUMN password_reset_token_verify_fail_count SET DEFAULT 0;

ALTER TABLE users
ALTER COLUMN email_verification_fail_count SET DEFAULT 0;