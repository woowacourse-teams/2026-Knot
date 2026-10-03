ALTER TABLE workspace_invitations
    ADD COLUMN code_expires_at TIMESTAMPTZ,
    ADD COLUMN link_token_expires_at TIMESTAMPTZ;

UPDATE workspace_invitations
SET code_expires_at = expires_at,
    link_token_expires_at = expires_at;

-- 구 버전 INSERT가 두 컬럼을 생략하면 기존 expires_at을 사용한다.
ALTER TABLE workspace_invitations
    ADD CONSTRAINT chk_workspace_invitations_channel_expirations
        CHECK (
            (code_expires_at IS NULL AND link_token_expires_at IS NULL)
            OR (code_expires_at IS NOT NULL AND link_token_expires_at IS NOT NULL
                AND code_expires_at > created_at AND link_token_expires_at > created_at)
        ),
    DROP CONSTRAINT fk_workspace_invitations_workspace,
    ADD CONSTRAINT fk_workspace_invitations_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE RESTRICT;

-- 단수 조회·재발급 전환 전까지 기존 단일 초대 UNIQUE와 expires_at CHECK를 유지한다.
