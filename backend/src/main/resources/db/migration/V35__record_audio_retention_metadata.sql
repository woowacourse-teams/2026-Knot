ALTER TABLE recording_audio_uploads
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN last_upload_url_expires_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_audio_deleted CHECK (
        deleted_at IS NULL OR (status = 'COMPLETED' AND deleted_at >= completed_at)
    ),
    ADD CONSTRAINT ck_audio_url_expiry CHECK (
        last_upload_url_expires_at IS NULL OR last_upload_url_expires_at >= reserved_at
    );

CREATE INDEX idx_audio_retention ON recording_audio_uploads(completed_at, id)
    WHERE status = 'COMPLETED' AND deleted_at IS NULL;
