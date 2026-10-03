CREATE TABLE auth_session_refresh_token_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    refresh_token_hash VARCHAR(64) NOT NULL,
    auth_session_id BIGINT NOT NULL,
    consumed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_auth_session_refresh_token_history PRIMARY KEY (id),
    CONSTRAINT uk_auth_session_refresh_token_history_hash UNIQUE (refresh_token_hash),
    CONSTRAINT fk_auth_session_refresh_token_history_session FOREIGN KEY (auth_session_id)
        REFERENCES auth_sessions (id) ON DELETE CASCADE,
    CONSTRAINT ck_auth_session_refresh_token_history_hash CHECK (refresh_token_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_auth_session_refresh_token_history_session_id
    ON auth_session_refresh_token_history (auth_session_id);
