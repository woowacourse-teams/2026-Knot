-- 연결 만료 회수 작업이 주기마다 활성 녹음 중 마지막 신호가 오래된 순서로 후보를 찾는다.
CREATE INDEX idx_recording_sessions_active_last_seen
    ON recording_sessions (last_seen_at, id)
    WHERE status IN ('RECORDING', 'PAUSED');
