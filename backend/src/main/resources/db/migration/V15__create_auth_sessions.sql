CREATE TABLE auth_sessions (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    member_id BIGINT NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT pk_auth_session PRIMARY KEY (id),
    CONSTRAINT fk_auth_session_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE RESTRICT,
    CONSTRAINT uk_auth_session_refresh_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT ck_auth_session_refresh_token_hash CHECK (refresh_token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_auth_session_expiry CHECK (created_at < expires_at AND expires_at <= absolute_expires_at),
    CONSTRAINT ck_auth_session_revoked_at CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE INDEX ix_auth_session_member_id ON auth_sessions (member_id);
