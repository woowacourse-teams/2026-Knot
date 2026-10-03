ALTER TABLE workspace_invitations
    ADD CONSTRAINT uk_workspace_invitations_id_workspace UNIQUE (id, workspace_id);

ALTER TABLE workspace_members
    ADD COLUMN source_invitation_id BIGINT,
    ADD CONSTRAINT chk_workspace_members_source_invitation_id_positive
        CHECK (source_invitation_id IS NULL OR source_invitation_id > 0),
    ADD CONSTRAINT fk_workspace_members_source_invitation
        FOREIGN KEY (source_invitation_id, workspace_id)
        REFERENCES workspace_invitations (id, workspace_id) ON DELETE RESTRICT;

CREATE INDEX idx_workspace_members_source_invitation
    ON workspace_members (source_invitation_id, workspace_id)
    WHERE source_invitation_id IS NOT NULL;
