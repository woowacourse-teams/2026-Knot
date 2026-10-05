CREATE TABLE recording_sessions (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    workspace_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    request_id UUID NOT NULL,
    tab_id UUID NOT NULL,
    control_token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    current_interval_started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    last_seen_at TIMESTAMPTZ NOT NULL,
    accumulated_recording_millis BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_recording_sessions PRIMARY KEY (id),
    CONSTRAINT uk_recording_sessions_member_request UNIQUE (member_id, request_id),
    CONSTRAINT fk_recording_sessions_workspace
        FOREIGN KEY (workspace_id)
        REFERENCES workspaces (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_recording_sessions_member
        FOREIGN KEY (member_id)
        REFERENCES members (id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_recording_sessions_status
        CHECK (status IN ('RECORDING', 'PAUSED', 'ENDED')),
    CONSTRAINT chk_recording_sessions_control_token_hash
        CHECK (control_token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_recording_sessions_accumulated_recording_millis
        CHECK (accumulated_recording_millis >= 0),
    CONSTRAINT chk_recording_sessions_status_timestamps
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
                AND last_seen_at = ended_at
            )
        )
);

CREATE UNIQUE INDEX uk_recording_sessions_member_active
    ON recording_sessions (member_id)
    WHERE status IN ('RECORDING', 'PAUSED');
