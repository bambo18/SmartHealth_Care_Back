-- V37__create_social_login_user.sql

-- 1. 기존 CHECK 제약 조건 삭제
-- 이 단계가 없다면, 두 번째 ALTER TABLE 문이 실패하거나 이전 제약 조건이 남아있게 됩니다.
ALTER TABLE roles
DROP CONSTRAINT IF EXISTS check_role_values;

-- 2. 새 역할 삽입 (이전 단계에서 삭제했으므로 성공해야 함)
INSERT INTO roles (name, description) VALUES ('SOCIAL_ACCOUNT_USER', '소셜 로그인 사용자');

-- 3. 현재 roles 테이블에 존재하는 모든 역할 이름을 포함하여 CHECK 제약 조건 재추가
ALTER TABLE roles
ADD CONSTRAINT check_role_values
CHECK (name IN (
    'ADMIN',
    'AI_MODEL_SERVICE',
    'SHELTER',
    'USER',
    'BANNED_USER',
    'DEACTIVATED_USER',
    'DELETED_USER',
    'SOCIAL_ACCOUNT_USER' -- 새로 추가된 역할
));