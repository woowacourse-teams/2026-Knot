CREATE TABLE search_evidences (
    message_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    rank SMALLINT NOT NULL,
    CONSTRAINT pk_search_evidences PRIMARY KEY (message_id, document_id),
    CONSTRAINT fk_search_evidences_message FOREIGN KEY (message_id) REFERENCES search_messages(id) ON DELETE RESTRICT,
    CONSTRAINT fk_search_evidences_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE RESTRICT,
    CONSTRAINT uk_search_evidences_message_rank UNIQUE (message_id, rank),
    CONSTRAINT ck_search_evidences_rank CHECK (rank BETWEEN 1 AND 3)
);
