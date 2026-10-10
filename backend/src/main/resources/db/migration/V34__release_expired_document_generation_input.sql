ALTER TABLE document_generation_batches
    ADD COLUMN released_transcript_id BIGINT,
    ADD COLUMN input_released_at TIMESTAMPTZ,
    DROP CONSTRAINT ck_generation_batch_state,
    ADD CONSTRAINT ck_generation_batch_released_input CHECK (
        (released_transcript_id IS NULL AND input_released_at IS NULL)
        OR (released_transcript_id IS NOT NULL AND released_transcript_id > 0 AND input_released_at IS NOT NULL
            AND finished_at IS NOT NULL AND input_released_at >= finished_at
            AND transcript_id IS NULL AND processing_status IN ('FAILED', 'NO_CONTENT')
            AND queued_count = 0 AND running_count = 0 AND succeeded_count = 0)
    ),
    ADD CONSTRAINT ck_generation_batch_state CHECK (
        (topic_registration_state = 'WAITING_CLASSIFICATION'
            AND (transcript_id IS NOT NULL OR input_released_at IS NOT NULL)
            AND registered_at IS NULL AND cleanup_requested_at IS NULL)
        OR (topic_registration_state = 'TOPICS_REGISTERED' AND registered_at IS NOT NULL
            AND cleanup_requested_at IS NULL)
        OR (topic_registration_state = 'NO_CONTENT' AND registered_at IS NOT NULL
            AND cleanup_requested_at IS NOT NULL)
    );

CREATE INDEX idx_generation_job_retention ON document_generation_jobs(expires_at, id) WHERE status = 'FAILED';
