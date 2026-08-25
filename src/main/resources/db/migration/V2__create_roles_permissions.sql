INSERT INTO roles (name, description) VALUES ('ADMIN', '시스템 관리자');
INSERT INTO roles (name, description) VALUES ('SHELTER', '보호소 계정');
INSERT INTO roles (name, description) VALUES ('USER', '일반 사용자');
INSERT INTO roles (name, description) VALUES ('UNVERIFIED_USER', '이메일 미인증 사용자');
INSERT INTO roles (name, description) VALUES ('DEACTIVATED_USER', '비활성화된 사용자');
INSERT INTO roles (name, description) VALUES ('DELETED_USER', '삭제된 사용자');
INSERT INTO roles (name, description) VALUES ('BANNED_USER', '차단된 사용자');

-- 인증 및 계정 관리 권한
INSERT INTO permissions (name, description) VALUES ('can_login', '사용자 로그인');
INSERT INTO permissions (name, description) VALUES ('can_reissue_token', '인증 토큰 재발급');
INSERT INTO permissions (name, description) VALUES ('can_reset_password', '비밀번호 재설정');
INSERT INTO permissions (name, description) VALUES ('can_deactivate_account', '계정 비활성화');
INSERT INTO permissions (name, description) VALUES ('can_reactivate_account', '계정 재활성화');
INSERT INTO permissions (name, description)
VALUES ('can_manage_email_verification', '사용자의 이메일 인증 절차 관리 (이메일 재전송 등)');

-- 일반 사용자 권한
INSERT INTO permissions (name, description) VALUES ('can_view_own_profile', '자신의 프로필 보기');
INSERT INTO permissions (name, description) VALUES ('can_edit_own_profile', '자신의 프로필 수정');
INSERT INTO permissions (name, description) VALUES ('can_view_other_user_profile', '다른 사용자 프로필 보기');
INSERT INTO permissions (name, description) VALUES ('can_follow_user', '사용자 팔로우');
INSERT INTO permissions (name, description) VALUES ('can_unfollow_user', '사용자 언팔로우');
INSERT INTO permissions (name, description) VALUES ('can_block_user', '사용자 차단');
INSERT INTO permissions (name, description) VALUES ('can_add_pet', '반려동물 추가');
INSERT INTO permissions (name, description) VALUES ('can_view_own_pets', '자신이 등록한 반려동물 목록 보기');
INSERT INTO permissions (name, description) VALUES ('can_view_own_pet_detail', '자신이 등록한 반려동물 상세 정보 보기');
INSERT INTO permissions (name, description) VALUES ('can_edit_pet', '반려동물 정보 수정');
INSERT INTO permissions (name, description) VALUES ('can_delete_pet', '반려동물 삭제');
INSERT INTO permissions (name, description) VALUES ('can_start_walk', '산책 기록 시작');
INSERT INTO permissions (name, description) VALUES ('can_end_walk', '산책 기록 종료');
INSERT INTO permissions (name, description) VALUES ('can_update_walk_record', '산책 기록 수정');
INSERT INTO permissions (name, description) VALUES ('can_view_own_walk_records', '자신의 산책 기록 목록 보기');
INSERT INTO permissions (name, description) VALUES ('can_view_own_walk_detail', '자신의 산책 기록 상세 보기');
INSERT INTO permissions (name, description) VALUES ('can_delete_walk_record', '산책 기록 삭제');
INSERT INTO permissions (name, description) VALUES ('can_view_weekly_summary', '주간 산책 요약 보기');
INSERT INTO permissions (name, description) VALUES ('can_use_health_check', '건강 검진 기능 사용');
INSERT INTO permissions (name, description) VALUES ('can_view_own_health_records', '자신의 건강 기록 보기');

-- 보호소 계정 권한
INSERT INTO permissions (name, description) VALUES ('can_view_shelter_profile', '보호소 프로필 보기');
INSERT INTO permissions (name, description) VALUES ('can_edit_shelter_profile', '보호소 프로필 수정');
INSERT INTO permissions (name, description) VALUES ('can_add_adoption_animal', '입양 동물 추가');
INSERT INTO permissions (name, description) VALUES ('can_view_adoption_animals', '입양 동물 목록 보기');
INSERT INTO permissions (name, description) VALUES ('can_view_adoption_animal_detail', '입양 동물 상세 정보 보기');
INSERT INTO permissions (name, description) VALUES ('can_edit_adoption_animal', '입양 동물 정보 수정');
INSERT INTO permissions (name, description) VALUES ('can_delete_adoption_animal', '입양 동물 삭제');

-- 관리자 권한
INSERT INTO permissions (name, description) VALUES ('can_manage_all_users', '모든 사용자 관리');
INSERT INTO permissions (name, description) VALUES ('can_manage_all_pets', '모든 반려동물 관리');
INSERT INTO permissions (name, description) VALUES ('can_manage_all_walk_records', '모든 산책 기록 관리');
INSERT INTO permissions (name, description) VALUES ('can_manage_all_health_records', '모든 건강 기록 관리');
INSERT INTO permissions (name, description) VALUES ('can_access_system_metrics', '시스템 지표 접근');
INSERT INTO permissions (name, description) VALUES ('can_assign_roles', '역할 할당');

-- 역할별 권한 매핑
-- ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT R.id, P.id
FROM roles R
CROSS JOIN permissions P
WHERE R.name = 'ADMIN' AND P.name IN (
    'can_reissue_token',
    'can_manage_all_walk_records',
    'can_manage_all_health_records',
    'can_access_system_metrics',
    'can_assign_roles'
);

-- SHELTER
INSERT INTO role_permissions (role_id, permission_id)
SELECT R.id, P.id
FROM roles R
CROSS JOIN permissions P
WHERE R.name = 'SHELTER' AND P.name IN (
    'can_login',
    'can_reissue_token',
    'can_reset_password',
    'can_view_shelter_profile',
    'can_edit_shelter_profile',
    'can_add_adoption_animal',
    'can_view_adoption_animals',
    'can_view_adoption_animal_detail',
    'can_edit_adoption_animal',
    'can_delete_adoption_animal'
);

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
    'can_view_own_health_records'
);

--- UNVERIFIED_USER
INSERT INTO role_permissions (role_id, permission_id)
SELECT R.id, P.id
FROM roles R
CROSS JOIN permissions P
WHERE R.name = 'UNVERIFIED_USER' AND P.name IN (
    'can_login',
    'can_reissue_token',
    'can_reset_password',
    'can_manage_email_verification'
);

-- DEACTIVATED_USER
INSERT INTO role_permissions (role_id, permission_id)
SELECT R.id, P.id
FROM roles R
CROSS JOIN permissions P
WHERE R.name = 'DEACTIVATED_USER' AND P.name IN (
    'can_login',
    'can_reissue_token',
    'can_reset_password',
    'can_reactivate_account'
);
