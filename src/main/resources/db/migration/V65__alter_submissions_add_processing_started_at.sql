-- =========================================================
-- V65: submissions.processing_started_at 추가 — 재시도 타임아웃 기준 교정
--
-- RetryAIInferenceTasks 는 지금 "submitted_at <= now - timeout" 으로 응답 지연을
-- 판정한다. 그런데 submitted_at 은 사용자가 제출한 시각이고 재시도해도 갱신되지
-- 않는다. 그래서 한 번 기준선을 넘고 나면 조건이 영구히 참이 되어, 2회차부터는
-- 타임아웃이 아니라 재시도 잡 주기(1분)만에 다시 걸린다 — 워커가 정상 처리
-- 중이어도 max-attempts 가 몇 분 만에 소진된다.
--
-- PROCESSING 진입 시각을 따로 기록해 시도마다 타임아웃 시계를 새로 돌린다.
-- submitted_at 을 덮어쓰지 않는 이유는 그것이 사용자에게 보여주는 제출 시각이기
-- 때문이다.
--
-- 같은 테이블의 submitted_at · completed_at 이 timestamp(6) 이므로 타입을 맞춘다.
-- =========================================================

ALTER TABLE submissions
    ADD COLUMN processing_started_at timestamp(6);

COMMENT ON COLUMN submissions.processing_started_at IS
    'PROCESSING 으로 전환된 시각(UTC). 재시도 타임아웃의 기준. 재적재할 때마다 갱신된다. NULL이면 아직 큐에 실린 적 없음';

-- 이미 PROCESSING 으로 떠 있는 행은 기준값이 없으면 영원히 재시도 대상에서 빠진다.
-- 기존 동작과 같아지도록 제출 시각으로 백필한다.
UPDATE submissions
   SET processing_started_at = submitted_at
 WHERE status = 'PROCESSING'
   AND processing_started_at IS NULL;

-- 재시도 잡의 조회 패턴: status = 'PROCESSING' AND processing_started_at <= ?
--                        ORDER BY processing_started_at ASC
CREATE INDEX idx_submissions_processing_started_at
    ON submissions (processing_started_at)
 WHERE status = 'PROCESSING';
