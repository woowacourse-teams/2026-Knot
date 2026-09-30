ALTER TABLE workspaces
    ADD COLUMN created_by_member_id BIGINT,
    ADD COLUMN deleted_at TIMESTAMPTZ;

DO $$
DECLARE
    invalid_workspace_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO invalid_workspace_count
    FROM (
        SELECT w.id
        FROM workspaces w
        LEFT JOIN workspace_members wm ON wm.workspace_id = w.id AND wm.role = 'OWNER'
        GROUP BY w.id
        HAVING COUNT(wm.id) <> 1
    ) invalid_workspaces;

    IF invalid_workspace_count > 0 THEN
        RAISE EXCEPTION 'Workspace creator backfill requires exactly one OWNER per workspace';
    END IF;
END $$;

UPDATE workspaces w
SET created_by_member_id = wm.member_id
FROM workspace_members wm
WHERE wm.workspace_id = w.id AND wm.role = 'OWNER';

ALTER TABLE workspaces
    ALTER COLUMN created_by_member_id SET NOT NULL,
    ADD CONSTRAINT fk_workspaces_created_by_member
        FOREIGN KEY (created_by_member_id) REFERENCES members (id) ON DELETE RESTRICT;

CREATE INDEX idx_workspaces_active_created_by_member
    ON workspaces (created_by_member_id)
    WHERE deleted_at IS NULL;
