CREATE TABLE search_conversations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    visible_in_list BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_search_conversations_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE RESTRICT,
    CONSTRAINT fk_search_conversations_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE RESTRICT
);

CREATE INDEX idx_search_conversations_visible_activity
    ON search_conversations (workspace_id, member_id, updated_at DESC, id DESC)
    WHERE visible_in_list = TRUE;

CREATE TABLE search_messages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    sequence INTEGER NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_search_messages_conversation FOREIGN KEY (conversation_id) REFERENCES search_conversations(id) ON DELETE RESTRICT,
    CONSTRAINT uk_search_messages_conversation_sequence UNIQUE (conversation_id, sequence),
    CONSTRAINT ck_search_messages_positive_sequence CHECK (sequence > 0),
    CONSTRAINT ck_search_messages_role_status CHECK (
        (role = 'USER' AND status = 'RECEIVED')
        OR (role = 'ASSISTANT' AND status IN ('STREAMING', 'COMPLETED', 'FAILED', 'STOPPED'))
    )
);
