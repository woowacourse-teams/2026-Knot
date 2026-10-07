CREATE TABLE document_generation_execution_requests (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    job_id BIGINT NOT NULL,
    attempt_count INTEGER NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_document_generation_execution_requests PRIMARY KEY (id),
    CONSTRAINT fk_document_generation_execution_requests_job FOREIGN KEY (job_id)
        REFERENCES document_generation_jobs (id) ON DELETE RESTRICT,
    CONSTRAINT uk_document_generation_execution_requests_attempt UNIQUE (job_id, attempt_count),
    CONSTRAINT chk_document_generation_execution_requests_attempt CHECK (attempt_count >= 1)
);
