-- =========================================================
-- V64: pet_health_certificates.ocr_confidence 를 NULL 허용으로 완화 (HEALTH-01)
--
-- 비동기 Celery 방식으로 전환하면서 Google Vision 호출이 AI 워커로 넘어갔다.
-- 워커가 돌려주는 결과에 신뢰도가 포함되지 않으면 백엔드는 이 값을 만들 수 없다.
-- NOT NULL 을 유지하면 진단서 기록 자체를 저장할 수 없으므로 제약을 푼다.
--
-- NULL 의 의미는 "신뢰도 미상"이다. 인식 품질 게이트(SPEC §6 규칙 3)는
-- 값이 있을 때만 적용하고, NULL 이면 통과시킨다 — 판정 근거가 없는 것이지
-- 품질이 낮다는 뜻이 아니다.
--
-- 기존 행은 전부 동기 구현이 채운 값이 있으므로 백필이 필요 없다.
-- =========================================================

ALTER TABLE pet_health_certificates
    ALTER COLUMN ocr_confidence DROP NOT NULL;

COMMENT ON COLUMN pet_health_certificates.ocr_confidence IS
    'OCR 전체 평균 신뢰도(0~1). NULL이면 신뢰도 미상 — AI 워커가 값을 제공하지 않은 경우';

-- chk_pet_health_certificates_ocr_confidence (0 <= x <= 1) 는 그대로 둔다.
-- PostgreSQL 의 CHECK 는 NULL 에 대해 UNKNOWN 을 돌려주고 이는 통과로 처리되므로,
-- NULL 허용과 충돌하지 않는다. 범위 검증은 값이 있을 때만 작동한다.
