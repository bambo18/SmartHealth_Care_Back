ALTER TABLE users
  ALTER COLUMN email_verification_fail_count TYPE smallint USING COALESCE(email_verification_fail_count, 0),
  ALTER COLUMN email_verification_fail_count SET DEFAULT 0,
  ALTER COLUMN email_verification_fail_count SET NOT NULL;

ALTER TABLE users
  ALTER COLUMN login_attempt TYPE smallint USING COALESCE(login_attempt, 0),
  ALTER COLUMN login_attempt SET DEFAULT 0,
  ALTER COLUMN login_attempt SET NOT NULL;

ALTER TABLE users
  ALTER COLUMN password_reset_token_verify_fail_count TYPE smallint USING COALESCE(password_reset_token_verify_fail_count, 0),
  ALTER COLUMN password_reset_token_verify_fail_count SET DEFAULT 0,
  ALTER COLUMN password_reset_token_verify_fail_count SET NOT NULL;
