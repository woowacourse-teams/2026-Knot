-- 종료 결과를 추측하지 않도록 기존 분류·주제·작업·문서를 먼저 대조한다.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM document_generation_batches b
        WHERE
            (SELECT count(*) FROM document_generation_batch_topics t WHERE t.batch_id = b.id)
                <> (SELECT count(*) FROM document_generation_jobs j WHERE j.batch_id = b.id AND j.stage = 'GENERATION')
            OR (b.topic_registration_state = 'WAITING_CLASSIFICATION' AND (
                EXISTS (SELECT 1 FROM document_generation_batch_topics t WHERE t.batch_id = b.id)
                OR (SELECT count(*) FROM document_generation_jobs j WHERE j.batch_id = b.id AND j.stage = 'CLASSIFICATION'
                    AND j.status <> 'SUCCEEDED') <> 1))
            OR (b.topic_registration_state = 'TOPICS_REGISTERED' AND (
                NOT EXISTS (SELECT 1 FROM document_generation_batch_topics t WHERE t.batch_id = b.id)
                OR NOT EXISTS (SELECT 1 FROM document_generation_jobs j WHERE j.batch_id = b.id
                    AND j.stage = 'CLASSIFICATION' AND j.status = 'SUCCEEDED')))
            OR (b.topic_registration_state = 'NO_CONTENT' AND (
                EXISTS (SELECT 1 FROM document_generation_jobs j WHERE j.batch_id = b.id AND j.status <> 'SUCCEEDED')
                OR EXISTS (SELECT 1 FROM document_generation_batch_topics t WHERE t.batch_id = b.id)))
    ) OR EXISTS (
        SELECT 1 FROM document_generation_jobs j
        WHERE j.stage = 'GENERATION' AND j.status = 'SUCCEEDED'
            AND NOT EXISTS (SELECT 1 FROM documents d WHERE d.document_generation_job_id = j.id)
    ) OR EXISTS (
        SELECT 1 FROM documents d JOIN document_generation_jobs j ON j.id = d.document_generation_job_id
        WHERE j.stage <> 'GENERATION' OR j.status <> 'SUCCEEDED'
            OR j.topic <> d.topic OR j.transcript_id <> d.source_transcript_id
    ) THEN
        RAISE EXCEPTION 'Document generation execution requires verified batch progress';
    END IF;
END $$;

ALTER TABLE document_generation_jobs
    ADD COLUMN next_attempt_at TIMESTAMPTZ,
    ADD COLUMN execution_deadline_at TIMESTAMPTZ,
    ADD COLUMN failure_cause VARCHAR(30);

UPDATE document_generation_jobs SET
    next_attempt_at = CASE WHEN status = 'QUEUED' THEN updated_at END,
    execution_deadline_at = CASE WHEN status = 'RUNNING' THEN updated_at END,
    failure_cause = CASE WHEN status = 'FAILED' THEN 'LEGACY_FAILURE' END;

ALTER TABLE document_generation_jobs
    ADD CONSTRAINT ck_generation_job_execution CHECK (
        (status = 'QUEUED' AND next_attempt_at IS NOT NULL AND next_attempt_at >= updated_at
            AND execution_deadline_at IS NULL AND failure_cause IS NULL)
        OR (status = 'RUNNING' AND next_attempt_at IS NULL AND execution_deadline_at IS NOT NULL
            AND execution_deadline_at >= updated_at AND failure_cause IS NULL)
        OR (status = 'SUCCEEDED' AND next_attempt_at IS NULL AND execution_deadline_at IS NULL AND failure_cause IS NULL)
        OR (status = 'FAILED' AND next_attempt_at IS NULL AND execution_deadline_at IS NULL AND failure_cause IS NOT NULL)
    ),
    ADD CONSTRAINT ck_generation_job_failure_cause CHECK (
        failure_cause IN ('TIMEOUT', 'RATE_LIMITED', 'UNAVAILABLE', 'AUTHENTICATION', 'INVALID_INPUT', 'INVALID_RESPONSE',
            'INPUT_LIMIT', 'OUTPUT_LIMIT', 'EXECUTION_EXPIRED', 'WORKSPACE_DELETED', 'STORAGE', 'INTERNAL', 'LEGACY_FAILURE')
    );

CREATE INDEX idx_generation_job_ready ON document_generation_jobs(next_attempt_at, id) WHERE status = 'QUEUED';
CREATE INDEX idx_generation_job_expired ON document_generation_jobs(execution_deadline_at, id) WHERE status = 'RUNNING';

ALTER TABLE document_generation_batches
    ADD COLUMN processing_status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    ADD COLUMN queued_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN running_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN succeeded_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN failed_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN finished_at TIMESTAMPTZ;

WITH counts AS (
    SELECT b.id,
        count(j.id) FILTER (WHERE j.stage = 'GENERATION' AND j.status = 'QUEUED') AS queued,
        count(j.id) FILTER (WHERE j.stage = 'GENERATION' AND j.status = 'RUNNING') AS running,
        count(j.id) FILTER (WHERE j.stage = 'GENERATION' AND j.status = 'SUCCEEDED') AS succeeded,
        count(j.id) FILTER (WHERE j.stage = 'GENERATION' AND j.status = 'FAILED') AS failed,
        max(j.updated_at) AS changed_at,
        max(j.status) FILTER (WHERE j.stage = 'CLASSIFICATION') AS classification_status
    FROM document_generation_batches b LEFT JOIN document_generation_jobs j ON j.batch_id = b.id
    GROUP BY b.id
), results AS (
    SELECT c.*,
        CASE WHEN b.topic_registration_state = 'NO_CONTENT' THEN 'NO_CONTENT'
            WHEN b.topic_registration_state = 'WAITING_CLASSIFICATION' THEN c.classification_status
            WHEN c.running > 0 THEN 'RUNNING'
            WHEN c.queued > 0 THEN 'QUEUED'
            WHEN c.failed > 0 THEN 'FAILED'
            ELSE 'SUCCEEDED' END AS overall_status,
        b.registered_at
    FROM document_generation_batches b JOIN counts c ON c.id = b.id
)
UPDATE document_generation_batches b SET
    queued_count = r.queued, running_count = r.running, succeeded_count = r.succeeded, failed_count = r.failed,
    processing_status = r.overall_status,
    finished_at = CASE WHEN r.overall_status = 'NO_CONTENT' THEN r.registered_at
        WHEN r.overall_status IN ('SUCCEEDED', 'FAILED') THEN r.changed_at END
FROM results r WHERE b.id = r.id;

ALTER TABLE document_generation_batches
    ADD CONSTRAINT ck_generation_batch_counts CHECK (
        queued_count >= 0 AND running_count >= 0 AND succeeded_count >= 0 AND failed_count >= 0
        AND (topic_registration_state = 'TOPICS_REGISTERED' OR
            queued_count + running_count + succeeded_count + failed_count = 0)
    ),
    ADD CONSTRAINT ck_generation_batch_processing CHECK (
        (processing_status IN ('QUEUED', 'RUNNING') AND finished_at IS NULL)
        OR (processing_status IN ('SUCCEEDED', 'FAILED', 'NO_CONTENT')
            AND finished_at IS NOT NULL AND finished_at >= accepted_at)
    ),
    ADD CONSTRAINT ck_generation_batch_no_content CHECK (
        (topic_registration_state = 'NO_CONTENT') = (processing_status = 'NO_CONTENT')
    );
