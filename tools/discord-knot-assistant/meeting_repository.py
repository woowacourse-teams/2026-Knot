from __future__ import annotations

import os
import sqlite3
from contextlib import closing
from pathlib import Path

import anyio
from pydantic import BaseModel, ConfigDict, ValidationError

from meeting_models import MeetingDraft, MeetingFailure


class MeetingRecord(BaseModel):
    model_config = ConfigDict(frozen=True, extra="forbid")

    message_id: int
    channel_id: int
    owner_id: int
    owner_name: str
    draft: MeetingDraft
    revision: int = 0
    cancelled: bool = False
    pending_draft: MeetingDraft | None = None
    pending_cancel: bool = False


class MeetingRepository:
    def __init__(self, path: Path | None = None) -> None:
        self.path = path or Path.home() / "Library/Application Support/KnotDiscordAssistant/meetings.sqlite3"
        self.lock = anyio.Lock()

    def _connect(self) -> sqlite3.Connection:
        self.path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        descriptor = os.open(self.path, os.O_CREAT | os.O_RDWR, 0o600)
        os.fchmod(descriptor, 0o600)
        os.close(descriptor)
        connection = sqlite3.connect(self.path, timeout=5)
        connection.execute("CREATE TABLE IF NOT EXISTS meetings (message_id INTEGER PRIMARY KEY, record TEXT NOT NULL)")
        return connection

    async def records(self) -> list[MeetingRecord]:
        return await anyio.to_thread.run_sync(self._records)

    def _records(self) -> list[MeetingRecord]:
        try:
            with closing(self._connect()) as connection:
                rows = connection.execute("SELECT record FROM meetings").fetchall()
            return [MeetingRecord.model_validate_json(row[0]) for row in rows]
        except (sqlite3.Error, OSError, ValidationError) as error:
            raise MeetingFailure("meeting_storage_read", "Mac의 회의 내역을 읽지 못했어. 운영 로그와 저장소를 확인해줘.") from error

    async def get(self, message_id: int) -> MeetingRecord:
        return await anyio.to_thread.run_sync(self._get, message_id)

    def _get(self, message_id: int) -> MeetingRecord:
        try:
            with closing(self._connect()) as connection:
                row = connection.execute("SELECT record FROM meetings WHERE message_id = ?", (message_id,)).fetchone()
            if row is None:
                raise MeetingFailure("meeting_missing", "저장된 회의 내역을 찾을 수 없어. 이 공지는 수정할 수 없어.")
            return MeetingRecord.model_validate_json(row[0])
        except (sqlite3.Error, OSError, ValidationError) as error:
            raise MeetingFailure("meeting_storage_read", "Mac의 회의 내역을 읽지 못했어. 운영 로그와 저장소를 확인해줘.") from error

    async def save(self, record: MeetingRecord) -> None:
        await anyio.to_thread.run_sync(self._save, record)

    def _save(self, record: MeetingRecord) -> None:
        try:
            with closing(self._connect()) as connection, connection:
                connection.execute("INSERT INTO meetings (message_id, record) VALUES (?, ?) ON CONFLICT(message_id) DO UPDATE SET record = excluded.record", (record.message_id, record.model_dump_json()))
        except (sqlite3.Error, OSError) as error:
            raise MeetingFailure("meeting_storage_write", "Mac의 회의 내역을 저장하지 못했어. 공지 상태와 운영 로그를 확인해줘.") from error
