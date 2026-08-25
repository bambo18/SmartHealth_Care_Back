ALTER TABLE permissions
DROP CONSTRAINT check_permission_name_values;

ALTER TABLE permissions
ADD CONSTRAINT check_permission_name_values
CHECK (name IN (
    -- Basic Account Management Permissions
    'can_reset_password',
    'can_deactivate_account',
    'can_reactivate_account',
    'can_manage_email_verification',

    -- General User Permissions (User & Profile)
    'can_view_own_profile',
    'can_edit_own_profile',
    'can_view_other_user_profile',
    'can_follow_user',
    'can_unfollow_user',
    'can_block_user',

    -- General User Permissions (Pet Management)
    'can_add_pet',
    'can_view_own_pets',
    'can_view_own_pet_detail',
    'can_edit_pet',
    'can_delete_pet',

    -- General User Permissions (Walk & Health Records)
    'can_start_walk',
    'can_end_walk',
    'can_update_walk_record',
    'can_view_own_walk_records',
    'can_view_own_walk_detail',
    'can_delete_walk_record',
    'can_view_weekly_summary',
    'can_use_health_check',
    'can_view_own_health_records',

    -- Shelter Account Permissions
    'can_view_shelter_profile',
    'can_edit_shelter_profile',
    'can_add_adoption_animal',
    'can_view_adoption_animals',
    'can_view_adoption_animal_detail',
    'can_edit_adoption_animal',
    'can_delete_adoption_animal',

    -- Administrator Permissions
    'can_manage_all_users',
    'can_manage_all_pets',
    'can_manage_all_shelters', -- New Permission
    'can_manage_all_walk_records',
    'can_manage_all_health_records',
    'can_access_system_metrics',
    'can_assign_roles',

    -- AI Model Account Permissions
    'can_update_health_records'
));

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
    'AI_MODEL_SERVICE'
));

-- 3. Insert the new role and permission
INSERT INTO roles (name, description) VALUES ('AI_MODEL_SERVICE', 'AI 모델 서비스 계정');

INSERT INTO permissions (name, description) VALUES ('can_update_health_records', '건강 기록 업데이트');

INSERT INTO role_permissions (role_id, permission_id)
SELECT R.id, P.id
FROM roles R
CROSS JOIN permissions P
WHERE R.name = 'AI_MODEL_SERVICE' AND P.name IN (
    'can_update_health_records'
);
