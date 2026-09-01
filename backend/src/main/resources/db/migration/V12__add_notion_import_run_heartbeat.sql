ALTER TABLE notion_import_runs
    ADD COLUMN last_heartbeat_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP;

UPDATE notion_import_runs
SET last_heartbeat_at = NULL
WHERE status IN ('COMPLETED', 'FAILED');

ALTER TABLE notion_import_runs
    ADD CONSTRAINT chk_notion_import_runs_heartbeat
        CHECK (status <> 'RUNNING' OR last_heartbeat_at IS NOT NULL);

CREATE INDEX idx_notion_import_runs_running_heartbeat
    ON notion_import_runs (last_heartbeat_at, id)
    WHERE status = 'RUNNING';
