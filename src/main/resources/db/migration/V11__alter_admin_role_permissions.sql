ALTER TABLE permissions
DROP CONSTRAINT check_permission_name_values;

ALTER TABLE permissions
ADD CONSTRAINT check_permission_name_values
CHECK (name IN (
    -- Basic Account Management Permissions
    'can_login',
    'can_reissue_token',
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
    'can_assign_roles'
));

INSERT INTO permissions (name, description) VALUES ('can_manage_all_shelters', '모든 보호소 관리');

-- Run this *once* if you haven't already defined a unique constraint
ALTER TABLE role_permissions 
ADD CONSTRAINT role_permissions_unique_role_permission 
UNIQUE (role_id, permission_id);

INSERT INTO role_permissions (role_id, permission_id)
SELECT R.id, P.id
FROM roles R
CROSS JOIN permissions P
WHERE R.name = 'ADMIN' 
  AND P.name IN (
    'can_reissue_token',
    'can_manage_all_walk_records',
    'can_manage_all_health_records',
    'can_access_system_metrics',
    'can_assign_roles',
    -- *** Add your new permissions here ***
    'can_manage_all_pets',
    'can_manage_all_users',
    'can_manage_all_shelters'  -- New Permission
    -- ... and any other new permissions
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- USER
INSERT INTO role_permissions (role_id, permission_id)
SELECT R.id, P.id
FROM roles R
CROSS JOIN permissions P
WHERE R.name = 'USER' AND P.name IN (
    -- 일반 사용자 권한 (General User Permissions)
    'can_login',
    'can_reissue_token',
    'can_reset_password',
    'can_deactivate_account',
    'can_reactivate_account',
    'can_view_own_profile',
    'can_edit_own_profile',
    'can_view_other_user_profile',
    'can_follow_user',
    'can_unfollow_user',
    'can_block_user',
    'can_add_pet',
    'can_view_own_pets',
    'can_view_own_pet_detail',
    'can_edit_pet',
    'can_delete_pet',
    'can_start_walk',
    'can_end_walk',
    'can_update_walk_record',
    'can_view_own_walk_records',
    'can_view_own_walk_detail',
    'can_delete_walk_record',
    'can_view_weekly_summary',
    'can_use_health_check',
    'can_view_own_health_records',
    'can_view_shelter_profile',
    'can_view_adoption_animals',
    'can_view_adoption_animal_detail'
) ON CONFLICT (role_id, permission_id) DO NOTHING;