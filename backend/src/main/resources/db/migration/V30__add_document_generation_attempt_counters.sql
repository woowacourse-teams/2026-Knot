ALTER TABLE document_generation_jobs
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER,
    ADD COLUMN IF NOT EXISTS user_retry_count INTEGER,
    ADD COLUMN IF NOT EXISTS automatic_retry_count INTEGER;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM document_generation_jobs
        WHERE attempt_count IS NULL OR user_retry_count IS NULL OR automatic_retry_count IS NULL
    ) THEN
        RAISE EXCEPTION 'Existing jobs require verified attempt counters before migration V30';
    END IF;
END $$;

ALTER TABLE document_generation_jobs
    ALTER COLUMN attempt_count SET DEFAULT 1,
    ALTER COLUMN attempt_count SET NOT NULL,
    ALTER COLUMN user_retry_count SET DEFAULT 0,
    ALTER COLUMN user_retry_count SET NOT NULL,
    ALTER COLUMN automatic_retry_count SET DEFAULT 0,
    ALTER COLUMN automatic_retry_count SET NOT NULL,
    ADD CONSTRAINT chk_document_generation_jobs_attempt_counts CHECK (
        attempt_count >= 1 AND user_retry_count BETWEEN 0 AND 3 AND automatic_retry_count >= 0
        AND attempt_count::BIGINT = 1::BIGINT + user_retry_count::BIGINT + automatic_retry_count::BIGINT
    );
