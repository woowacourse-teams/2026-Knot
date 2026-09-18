ALTER TABLE workspace_members
    ADD COLUMN last_viewed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX uk_workspace_members_member_last_viewed
    ON workspace_members (member_id)
    WHERE last_viewed;
