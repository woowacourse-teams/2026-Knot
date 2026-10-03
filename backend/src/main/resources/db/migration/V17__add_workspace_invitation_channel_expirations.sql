ALTER TABLE workspace_invitations
    ADD COLUMN link_token_expires_at TIMESTAMPTZ,
    ADD COLUMN invite_code_expires_at TIMESTAMPTZ;

UPDATE workspace_invitations
SET link_token_expires_at = expires_at,
    invite_code_expires_at = expires_at;

ALTER TABLE workspace_invitations
    DROP CONSTRAINT chk_workspace_invitations_expiration,
    ADD CONSTRAINT chk_workspace_invitations_expiration_after_creation
        CHECK (expires_at > created_at),
    ADD CONSTRAINT chk_workspace_invitations_link_token_expiration
        CHECK (link_token_expires_at IS NULL OR link_token_expires_at > created_at),
    ADD CONSTRAINT chk_workspace_invitations_invite_code_expiration
        CHECK (invite_code_expires_at IS NULL OR invite_code_expires_at > created_at),
    ADD CONSTRAINT chk_workspace_invitations_channel_expiration_pair
        CHECK (
            (link_token_expires_at IS NULL AND invite_code_expires_at IS NULL)
            OR
            (link_token_expires_at IS NOT NULL AND invite_code_expires_at IS NOT NULL)
        ),
    DROP CONSTRAINT fk_workspace_invitations_workspace,
    ADD CONSTRAINT fk_workspace_invitations_workspace
        FOREIGN KEY (workspace_id)
        REFERENCES workspaces (id)
        ON DELETE RESTRICT;

DROP INDEX uk_workspace_invitations_one_uninvalidated;
