ALTER TABLE workspaces
    ADD COLUMN deleted_at TIMESTAMPTZ;

ALTER TABLE workspace_members
    ADD COLUMN left_at TIMESTAMPTZ;

ALTER TABLE workspace_members
    DROP CONSTRAINT uk_workspace_members_workspace_member;

DROP INDEX uk_workspace_members_member_last_viewed;

ALTER TABLE workspaces
    ADD CONSTRAINT chk_workspaces_deleted_at_after_created_at
        CHECK (deleted_at IS NULL OR deleted_at >= created_at);

ALTER TABLE workspace_members
    ADD CONSTRAINT chk_workspace_members_left_at_after_joined_at
        CHECK (left_at IS NULL OR left_at >= joined_at),
    ADD CONSTRAINT chk_workspace_members_left_not_last_viewed
        CHECK (left_at IS NULL OR last_viewed = FALSE);

CREATE UNIQUE INDEX uk_workspace_members_active_workspace_member
    ON workspace_members (workspace_id, member_id)
    WHERE left_at IS NULL;

CREATE UNIQUE INDEX uk_workspace_members_member_last_viewed
    ON workspace_members (member_id)
    WHERE last_viewed AND left_at IS NULL;
