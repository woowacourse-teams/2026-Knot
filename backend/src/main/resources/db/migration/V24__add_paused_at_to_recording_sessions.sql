ALTER TABLE recording_sessions
    ADD COLUMN paused_at TIMESTAMPTZ;

UPDATE recording_sessions
SET paused_at = last_seen_at
WHERE status = 'PAUSED'
  AND paused_at IS NULL;

ALTER TABLE recording_sessions
    ADD CONSTRAINT chk_recording_sessions_paused_at_after_started_at
        CHECK (paused_at IS NULL OR paused_at >= started_at);
