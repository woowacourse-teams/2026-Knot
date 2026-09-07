-- 탐색 근거를 페이지 단위 최대 3개에서 청크 단위 최대 8개로 넓힌다(데스크톱 기획서 6.4, 로드맵 S1).
-- V13까지 저장된 행은 페이지 단위라 청크 정보가 없으므로 chunk_index를 0으로 채운다(로드맵 Q32).
ALTER TABLE search_references
    ADD COLUMN chunk_index SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE search_references
    ALTER COLUMN chunk_index DROP DEFAULT;

ALTER TABLE search_references
    DROP CONSTRAINT uk_search_references_message_page;

ALTER TABLE search_references
    ADD CONSTRAINT uk_search_references_message_page_chunk
        UNIQUE (message_id, imported_page_id, chunk_index);

ALTER TABLE search_references
    DROP CONSTRAINT chk_search_references_rank;

ALTER TABLE search_references
    ADD CONSTRAINT chk_search_references_rank
        CHECK (reference_rank BETWEEN 1 AND 8);

ALTER TABLE search_references
    ADD CONSTRAINT chk_search_references_chunk_index
        CHECK (chunk_index >= 0);

-- 클라이언트(데스크톱)가 만든 답변을 서버가 만든 답변과 구분한다(로드맵 Q25).
ALTER TABLE chat_messages
    ADD COLUMN generated_by VARCHAR(10) NOT NULL DEFAULT 'SERVER';

ALTER TABLE chat_messages
    ADD CONSTRAINT chk_chat_messages_generated_by
        CHECK (generated_by IN ('SERVER', 'CLIENT'));
