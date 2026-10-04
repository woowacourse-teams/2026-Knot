ALTER TABLE recording_sessions
    DROP CONSTRAINT chk_recording_sessions_status,
    DROP CONSTRAINT chk_recording_sessions_status_timestamps,
    ADD CONSTRAINT chk_recording_sessions_status
        CHECK (status IN ('RECORDING', 'PAUSED', 'ENDED', 'DISCARDED')),
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
                status IN ('ENDED', 'DISCARDED')
                AND current_interval_started_at IS NULL
                AND ended_at IS NOT NULL
                AND ended_at >= started_at
                AND last_seen_at = ended_at
            )
        );
