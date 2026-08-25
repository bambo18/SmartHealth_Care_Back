ALTER TABLE email_verifications
ALTER COLUMN email_verification_token TYPE VARCHAR(255);

ALTER TABLE users
ALTER COLUMN email_verification_token TYPE VARCHAR(255);