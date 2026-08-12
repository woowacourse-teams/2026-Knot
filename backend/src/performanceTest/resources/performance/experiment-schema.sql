CREATE TABLE experiment_compact_blocks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    page_object_id BIGINT NOT NULL,
    source_block_object_id BIGINT NOT NULL,
    parent_block_object_id BIGINT,
    position_key TEXT NOT NULL,
    block_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    archived_at TIMESTAMPTZ,

    CONSTRAINT uk_experiment_compact_blocks_source
        UNIQUE (source_block_object_id),

    CONSTRAINT uk_experiment_compact_blocks_position
        UNIQUE NULLS NOT DISTINCT (page_object_id, parent_block_object_id, position_key),

    CONSTRAINT fk_experiment_compact_blocks_page
        FOREIGN KEY (page_object_id)
        REFERENCES pages (object_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_experiment_compact_blocks_source
        FOREIGN KEY (source_block_object_id)
        REFERENCES blocks (object_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_experiment_compact_blocks_page_parent_position
    ON experiment_compact_blocks (page_object_id, parent_block_object_id, position_key);
