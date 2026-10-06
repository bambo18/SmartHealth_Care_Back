-- =========================================================
-- V53: Grant search permissions to SOCIAL_ACCOUNT_USER
-- - can_view_hospitals
-- - can_view_shelters
--
-- V52__add_search_permissions.sql은 검색 권한을 USER/ADMIN에만
-- 연결했기 때문에, 카카오 등 소셜 계정으로 가입한 사용자
-- (SOCIAL_ACCOUNT_USER)는 병원/보호소 검색 API에서 403을 받았다.
-- 소셜 계정도 일반 사용자와 동일하게 검색이 가능해야 하므로
-- 동일한 두 권한을 SOCIAL_ACCOUNT_USER 역할에 추가한다.
-- =========================================================


-- 1. Give search permissions to SOCIAL_ACCOUNT_USER

INSERT INTO role_permissions (role_id, permission_id)
SELECT R.id, P.id
FROM roles R
CROSS JOIN permissions P
WHERE R.name = 'SOCIAL_ACCOUNT_USER'
  AND P.name IN (
      'can_view_shelters',
      'can_view_hospitals'
  )
ON CONFLICT DO NOTHING;
