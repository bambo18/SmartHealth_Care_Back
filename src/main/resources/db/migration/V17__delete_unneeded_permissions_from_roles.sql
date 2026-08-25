-- DELETE 'can_login' and 'can_reissue_token' from all roles

DELETE FROM role_permissions
WHERE permission_id IN (
    SELECT id
    FROM permissions
    WHERE name IN (
        'can_login',
        'can_reissue_token'
    )
);

-- DELETE the 'can_login' and 'can_reissue_token' permission definitions
DELETE FROM permissions
WHERE name IN (
    'can_login',
    'can_reissue_token'
);

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
    'can_assign_roles'
));