ALTER TABLE workspace_invitations
    DROP CONSTRAINT fk_workspace_invitations_workspace,
    ADD CONSTRAINT fk_workspace_invitations_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE RESTRICT;

-- 코드와 링크는 기존 expires_at 하나로 함께 만료한다.
-- 단수 조회·재발급 전환 전까지 기존 단일 초대 UNIQUE를 유지한다.
