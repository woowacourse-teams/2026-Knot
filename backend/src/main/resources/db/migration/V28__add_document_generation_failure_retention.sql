DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM document_generation_jobs WHERE status = 'FAILED') THEN
        RAISE EXCEPTION 'Existing FAILED jobs require verified failure timestamps before migration V28';
    END IF;
END $$;

ALTER TABLE document_generation_jobs
    ADD COLUMN last_failed_at TIMESTAMPTZ,
    ADD COLUMN expires_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_document_generation_jobs_failure_times CHECK (
        (last_failed_at IS NULL AND expires_at IS NULL)
        OR (last_failed_at IS NOT NULL AND expires_at IS NOT NULL
            AND last_failed_at >= created_at AND updated_at >= last_failed_at
            AND expires_at = last_failed_at + INTERVAL '168 hours')
    ),
    ADD CONSTRAINT chk_document_generation_jobs_failed_timestamp CHECK (
        status <> 'FAILED' OR last_failed_at IS NOT NULL
    );
