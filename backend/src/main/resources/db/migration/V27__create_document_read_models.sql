ALTER TABLE recording_sessions
    ADD CONSTRAINT uk_recording_sessions_id_workspace UNIQUE (id, workspace_id);

CREATE TABLE transcripts (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    recording_session_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_transcripts PRIMARY KEY (id),
    CONSTRAINT uk_transcripts_id_recording UNIQUE (id, recording_session_id),
    CONSTRAINT fk_transcripts_recording FOREIGN KEY (recording_session_id)
        REFERENCES recording_sessions (id) ON DELETE RESTRICT
);

CREATE INDEX idx_transcripts_recording ON transcripts (recording_session_id);

CREATE TABLE document_generation_jobs (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    transcript_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_document_generation_jobs PRIMARY KEY (id),
    CONSTRAINT uk_document_generation_jobs_id_transcript UNIQUE (id, transcript_id),
    CONSTRAINT fk_document_generation_jobs_transcript FOREIGN KEY (transcript_id)
        REFERENCES transcripts (id) ON DELETE RESTRICT,
    CONSTRAINT chk_document_generation_jobs_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_document_generation_jobs_updated_at CHECK (updated_at >= created_at)
);

CREATE INDEX idx_document_generation_jobs_transcript ON document_generation_jobs (transcript_id);

CREATE TABLE documents (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    workspace_id BIGINT NOT NULL,
    recording_session_id BIGINT NOT NULL,
    source_transcript_id BIGINT NOT NULL,
    document_generation_job_id BIGINT NOT NULL,
    topic TEXT NOT NULL,
    title TEXT NOT NULL,
    summary TEXT,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ,
    CONSTRAINT pk_documents PRIMARY KEY (id),
    CONSTRAINT uk_documents_generation_job UNIQUE (document_generation_job_id),
    CONSTRAINT uk_documents_recording_topic UNIQUE (recording_session_id, topic),
    CONSTRAINT fk_documents_recording_workspace FOREIGN KEY (recording_session_id, workspace_id)
        REFERENCES recording_sessions (id, workspace_id) ON DELETE RESTRICT,
    CONSTRAINT fk_documents_transcript_recording FOREIGN KEY (source_transcript_id, recording_session_id)
        REFERENCES transcripts (id, recording_session_id) ON DELETE RESTRICT,
    CONSTRAINT fk_documents_job_transcript FOREIGN KEY (document_generation_job_id, source_transcript_id)
        REFERENCES document_generation_jobs (id, transcript_id) ON DELETE RESTRICT,
    CONSTRAINT chk_documents_topic_not_blank CHECK (btrim(topic) <> ''),
    CONSTRAINT chk_documents_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT chk_documents_content_not_blank CHECK (btrim(content) <> ''),
    CONSTRAINT chk_documents_archive_state CHECK (
        (status = 'DRAFT' AND archived_at IS NULL)
        OR (status = 'ARCHIVED' AND archived_at IS NOT NULL AND archived_at >= created_at)
    )
);

CREATE INDEX idx_documents_workspace_created ON documents (workspace_id, created_at DESC, id DESC);

CREATE TABLE document_confirmations (
    document_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    confirmed_at TIMESTAMPTZ,
    CONSTRAINT pk_document_confirmations PRIMARY KEY (document_id, member_id),
    CONSTRAINT fk_document_confirmations_document FOREIGN KEY (document_id)
        REFERENCES documents (id) ON DELETE RESTRICT,
    CONSTRAINT fk_document_confirmations_member FOREIGN KEY (member_id)
        REFERENCES members (id) ON DELETE RESTRICT
);

CREATE INDEX idx_document_confirmations_member ON document_confirmations (member_id, document_id);
