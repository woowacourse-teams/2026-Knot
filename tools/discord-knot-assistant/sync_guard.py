from __future__ import annotations

import fcntl
import os
from pathlib import Path
from time import time

from pydantic import BaseModel, ValidationError


SYNC_STATE = Path.home() / "Library/Application Support/KnotDiscordAssistant/notion-sync.json"


class SyncState(BaseModel):
    started_at: float | None = None


def claim_sync(state_path: Path = SYNC_STATE) -> bool:
    state_path.parent.mkdir(parents=True, exist_ok=True)
    descriptor = os.open(state_path, os.O_RDWR | os.O_CREAT, 0o600)
    os.fchmod(descriptor, 0o600)
    with os.fdopen(descriptor, "r+", encoding="utf-8") as state_file:
        fcntl.flock(state_file, fcntl.LOCK_EX)
        raw = state_file.read()
        try:
            state = SyncState.model_validate_json(raw) if raw else SyncState()
        except ValidationError:
            state = SyncState()
        now = time()
        if state.started_at is not None and 0 <= now - state.started_at < 7200:
            return False
        state_file.seek(0)
        state_file.truncate()
        state_file.write(SyncState(started_at=now).model_dump_json())
        return True


def finish_sync(state_path: Path = SYNC_STATE) -> None:
    if not state_path.exists():
        return
    descriptor = os.open(state_path, os.O_RDWR)
    os.fchmod(descriptor, 0o600)
    with os.fdopen(descriptor, "r+", encoding="utf-8") as state_file:
        fcntl.flock(state_file, fcntl.LOCK_EX)
        state_file.seek(0)
        state_file.truncate()
        state_file.write(SyncState().model_dump_json())
