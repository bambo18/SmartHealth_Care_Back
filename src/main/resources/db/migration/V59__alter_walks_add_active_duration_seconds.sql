ALTER TABLE walks
    ADD COLUMN active_duration_seconds BIGINT;

COMMENT ON COLUMN walks.active_duration_seconds IS
    '일시정지를 제외한 실제 활동 시간(초). NULL이면 end_time - start_time으로 계산';
