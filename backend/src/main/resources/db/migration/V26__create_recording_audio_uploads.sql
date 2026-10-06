CREATE TABLE recording_audio_uploads (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recording_id   BIGINT      NOT NULL,
    storage_key    TEXT        NOT NULL,
    content_type   TEXT        NOT NULL,
    content_length BIGINT      NOT NULL,
    status         TEXT        NOT NULL,
    reserved_at    TIMESTAMPTZ NOT NULL,
    completed_at   TIMESTAMPTZ,
    CONSTRAINT fk_recording_audio_uploads_recording
        FOREIGN KEY (recording_id)
        REFERENCES recording_sessions (id)
        ON DELETE RESTRICT,
    CONSTRAINT uk_recording_audio_uploads_recording
        UNIQUE (recording_id),
    CONSTRAINT uk_recording_audio_uploads_storage_key
        UNIQUE (storage_key),
    CONSTRAINT chk_recording_audio_uploads_status
        CHECK (status IN ('RESERVED', 'COMPLETED')),
    CONSTRAINT chk_recording_audio_uploads_content_length
        CHECK (content_length > 0),
    CONSTRAINT chk_recording_audio_uploads_completed_at
        CHECK (
            (status = 'RESERVED' AND completed_at IS NULL)
            OR (status = 'COMPLETED' AND completed_at IS NOT NULL AND completed_at >= reserved_at)
        )
);
