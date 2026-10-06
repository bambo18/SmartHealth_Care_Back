-- =========================================================
-- V54: Remove can_view_hospitals permission
--
-- 병원 검색 기능(HospitalSearchController / HospitalSearchService /
-- clients/naver/*)을 제거했으므로, 그 기능 전용 권한도 함께 제거한다.
--
-- 삭제 순서가 중요하다:
--   1) role_permissions 참조 행  2) permissions 행  3) CHECK 제약
-- 제약을 먼저 좁히면 기존 행이 제약을 위반해 ALTER 가 실패한다.
--
-- can_view_shelters 는 보호소 검색이 그대로 살아 있으므로 유지한다.
-- =========================================================


-- 1. 역할 부여 해제 (V52 가 USER, ADMIN 에 부여했다)

DELETE FROM role_permissions
WHERE permission_id IN (
    SELECT id FROM permissions WHERE name = 'can_view_hospitals'
);


-- 2. 권한 행 삭제

DELETE FROM permissions
WHERE name = 'can_view_hospitals';


-- 3. CHECK 제약에서 값 제거
--    PermissionEnum 에서 상수를 지우므로, DB 가 이 값을 다시 받아들이면
--    PermissionConverter 가 변환할 수 없는 행이 생길 수 있다.

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

    -- Search Permissions
    'can_view_shelters',

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
    'can_manage_all_shelters',
    'can_manage_all_walk_records',
    'can_manage_all_health_records',
    'can_access_system_metrics',
    'can_assign_roles',

    -- AI Model Account Permissions
    'can_update_health_records'
));
