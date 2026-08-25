-- V3__Create_AI_Service_Account.sql

INSERT INTO users (
    -- Mandatory fields
    role_id,
    created_at,
    updated_at,
    nickname,
    email,
    password,

    -- Required-but-irrelevant fields, set to safe defaults
    login_attempt,
    email_verification_fail_count,
    password_reset_token_verify_fail_count
)
VALUES (
    -- 1. Role ID: Dynamically find the ID for the 'AI_MODEL_SERVICE' role
    --    (Assumes your roles table is named 'roles' and has a 'name' column)
    (SELECT id FROM roles WHERE name = 'AI_MODEL_SERVICE'),

    -- 2. Timestamps
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,

    -- 3. Nickname (Used for display/audit)
    'AI_MODEL_SERVICE_ACCOUNT',

    -- 4. Email (Unique login identifier)
    '${ai.user.email}',

    -- 5. Password: The full BCrypt hash injected from the CI/CD pipeline
    '${ai.user.password-hash}',

    -- 6. Defaults
    0,   -- login_attempt
    0,   -- email_verification_fail_count
    0    -- password_reset_token_verify_fail_count
);