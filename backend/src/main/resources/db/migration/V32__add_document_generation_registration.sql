-- 기존 Job에는 분류/생성 단계와 주제를 증명할 정보가 없다. 임의로 이행하지 않는다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM document_generation_jobs) THEN
        RAISE EXCEPTION 'Document generation registration requires verified legacy job stages and topics';
    END IF;
END $$;

CREATE TABLE document_generation_batches (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recording_session_id BIGINT NOT NULL UNIQUE,
    transcript_id BIGINT UNIQUE,
    topic_registration_state VARCHAR(30) NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL,
    registered_at TIMESTAMPTZ,
    cleanup_requested_at TIMESTAMPTZ,
    CONSTRAINT uk_generation_batch_input UNIQUE (id, transcript_id),
    CONSTRAINT fk_generation_batch_recording FOREIGN KEY (recording_session_id)
        REFERENCES recording_sessions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_generation_batch_transcript FOREIGN KEY (transcript_id, recording_session_id)
        REFERENCES transcripts(id, recording_session_id) ON DELETE RESTRICT,
    CONSTRAINT ck_generation_batch_state CHECK (
        (topic_registration_state = 'WAITING_CLASSIFICATION' AND transcript_id IS NOT NULL
            AND registered_at IS NULL AND cleanup_requested_at IS NULL)
        OR (topic_registration_state = 'TOPICS_REGISTERED' AND registered_at IS NOT NULL
            AND cleanup_requested_at IS NULL)
        OR (topic_registration_state = 'NO_CONTENT' AND registered_at IS NOT NULL
            AND cleanup_requested_at IS NOT NULL)
    ),
    CONSTRAINT ck_generation_batch_times CHECK (
        (registered_at IS NULL OR registered_at >= accepted_at)
        AND (cleanup_requested_at IS NULL OR cleanup_requested_at = registered_at)
    )
);

CREATE TABLE document_generation_batch_topics (
    batch_id BIGINT NOT NULL REFERENCES document_generation_batches(id) ON DELETE RESTRICT,
    position INTEGER NOT NULL CHECK (position >= 0),
    topic TEXT NOT NULL CHECK (topic !~ '^[[:space:]]*$'),
    PRIMARY KEY (batch_id, position),
    CONSTRAINT uk_generation_batch_topic UNIQUE (batch_id, topic)
);

ALTER TABLE document_generation_jobs
    ADD COLUMN batch_id BIGINT NOT NULL,
    ADD COLUMN stage VARCHAR(20) NOT NULL,
    ADD COLUMN topic TEXT,
    ADD CONSTRAINT fk_generation_job_batch FOREIGN KEY (batch_id)
        REFERENCES document_generation_batches(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_generation_job_batch_input FOREIGN KEY (batch_id, transcript_id)
        REFERENCES document_generation_batches(id, transcript_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_generation_job_topic FOREIGN KEY (batch_id, topic)
        REFERENCES document_generation_batch_topics(batch_id, topic) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_generation_job_stage CHECK (
        (stage = 'CLASSIFICATION' AND topic IS NULL)
        OR (stage = 'GENERATION' AND topic IS NOT NULL AND topic !~ '^[[:space:]]*$')
    ),
    ADD CONSTRAINT uk_generation_job_topic UNIQUE (batch_id, topic);

CREATE UNIQUE INDEX uk_generation_job_classification
    ON document_generation_jobs(batch_id) WHERE stage = 'CLASSIFICATION';
