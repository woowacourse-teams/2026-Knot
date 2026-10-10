-- 연결 만료 종료는 마지막 유효 신호 시각을 보존하고 그 120초 뒤를 종료 시각으로 기록한다.
-- 사용자 종료와 연결 만료를 구분할 사유를 저장하고, ENDED의 마지막 신호 시각이 종료 시각보다 앞설 수 있게 한다.
-- 이 migration 이전에 종료된 녹음의 사유는 알 수 없으므로 NULL로 둔다.
ALTER TABLE recording_sessions
    ADD COLUMN end_reason VARCHAR(30);

ALTER TABLE recording_sessions
    DROP CONSTRAINT chk_recording_sessions_status_timestamps,
    ADD CONSTRAINT chk_recording_sessions_status_timestamps
        CHECK (
            (
                status = 'RECORDING'
                AND current_interval_started_at IS NOT NULL
                AND ended_at IS NULL
                AND current_interval_started_at >= started_at
                AND last_seen_at >= current_interval_started_at
            )
            OR (
                status = 'PAUSED'
                AND current_interval_started_at IS NULL
                AND ended_at IS NULL
                AND last_seen_at >= started_at
            )
            OR (
                status = 'ENDED'
                AND current_interval_started_at IS NULL
                AND ended_at IS NOT NULL
                AND ended_at >= started_at
                AND last_seen_at <= ended_at
            )
            OR (
                status = 'DISCARDED'
                AND current_interval_started_at IS NULL
                AND ended_at IS NOT NULL
                AND ended_at >= started_at
                AND last_seen_at = ended_at
            )
        ),
    ADD CONSTRAINT chk_recording_sessions_end_reason
        CHECK (
            end_reason IS NULL
            OR (status = 'ENDED' AND end_reason IN ('USER_ENDED', 'CONNECTION_EXPIRED'))
        );
