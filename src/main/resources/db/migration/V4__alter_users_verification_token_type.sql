ALTER TABLE users
ALTER COLUMN email_verification_token TYPE varchar(8);

ALTER TABLE users
ALTER COLUMN password_reset_token TYPE varchar(255);