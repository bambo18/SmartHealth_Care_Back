-- 1. Delete associated permissions for UNVERIFIED_USER
DELETE FROM role_permissions
WHERE role_id = (SELECT id FROM roles WHERE name = 'UNVERIFIED_USER');

-- 2. Delete the UNVERIFIED_USER role itself
DELETE FROM roles
WHERE name = 'UNVERIFIED_USER';

-- 1. Drop the existing CHECK constraint
ALTER TABLE roles
DROP CONSTRAINT check_role_values;

-- 2. Re-add the CHECK constraint without 'UNVERIFIED_USER'
ALTER TABLE roles
ADD CONSTRAINT check_role_values
CHECK (name IN (
    'ADMIN',
    'SHELTER',
    'USER',
    'BANNED_USER',
    'DEACTIVATED_USER',
    'DELETED_USER',
    'SOCIAL_ACCOUNT_USER'
));