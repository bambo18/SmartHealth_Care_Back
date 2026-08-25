ALTER TABLE users
ADD COLUMN email_verification_tries INT NOT NULL DEFAULT 0;