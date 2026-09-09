-- 서버가 모델을 불러 만든 답변의 토큰 사용량을 답변과 같은 행에 남긴다(데스크톱 기획서 6.2 계측 행, 로드맵 B2).
-- 값이 있는 것은 서버가 만든 ASSISTANT 메시지뿐이다. USER·안내 문구 폴백·generated_by=CLIENT 턴은 전부 NULL이다.
ALTER TABLE chat_messages
    ADD COLUMN llm_model VARCHAR(100),
    ADD COLUMN input_tokens BIGINT,
    ADD COLUMN output_tokens BIGINT,
    ADD COLUMN cache_read_input_tokens BIGINT,
    ADD COLUMN cache_creation_input_tokens BIGINT;

ALTER TABLE chat_messages
    ADD CONSTRAINT chk_chat_messages_input_tokens_not_negative
        CHECK (input_tokens IS NULL OR input_tokens >= 0),
    ADD CONSTRAINT chk_chat_messages_output_tokens_not_negative
        CHECK (output_tokens IS NULL OR output_tokens >= 0),
    ADD CONSTRAINT chk_chat_messages_cache_read_input_tokens_not_negative
        CHECK (cache_read_input_tokens IS NULL OR cache_read_input_tokens >= 0),
    ADD CONSTRAINT chk_chat_messages_cache_creation_input_tokens_not_negative
        CHECK (cache_creation_input_tokens IS NULL OR cache_creation_input_tokens >= 0),
    ADD CONSTRAINT chk_chat_messages_llm_model_not_blank
        CHECK (llm_model IS NULL OR btrim(llm_model) <> '');
