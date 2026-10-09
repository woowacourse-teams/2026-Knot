CREATE TABLE recording_audio_deletion_tasks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    upload_id BIGINT NOT NULL UNIQUE REFERENCES recording_audio_uploads(id) ON DELETE RESTRICT,
    storage_key VARCHAR(255) NOT NULL CHECK (storage_key !~ '^[[:space:]]*$'),
    status VARCHAR(20) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    confirmation_after TIMESTAMPTZ NOT NULL CHECK (confirmation_after >= requested_at),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ,
    execution_deadline_at TIMESTAMPTZ,
    last_failure_code VARCHAR(50),
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_audio_deletion_state CHECK (
        (status = 'PENDING' AND next_attempt_at >= requested_at AND next_attempt_at IS NOT NULL
            AND execution_deadline_at IS NULL AND completed_at IS NULL)
        OR (status = 'RUNNING' AND next_attempt_at IS NULL AND attempt_count > 0
            AND execution_deadline_at > requested_at AND execution_deadline_at IS NOT NULL AND completed_at IS NULL)
        OR (status = 'SUCCEEDED' AND next_attempt_at IS NULL AND execution_deadline_at IS NULL
            AND completed_at >= confirmation_after AND completed_at IS NOT NULL)
    )
);

CREATE INDEX idx_audio_deletion_ready ON recording_audio_deletion_tasks(next_attempt_at, id) WHERE status = 'PENDING';
CREATE INDEX idx_audio_deletion_expired ON recording_audio_deletion_tasks(execution_deadline_at, id) WHERE status = 'RUNNING';
