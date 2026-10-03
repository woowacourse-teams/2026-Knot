DROP INDEX uk_workspace_invitations_one_uninvalidated;

CREATE INDEX idx_workspace_invitations_workspace_created
    ON workspace_invitations (workspace_id, created_at DESC, id DESC)
    WHERE invalidated_at IS NULL;
