from __future__ import annotations

import re
import socket
from time import monotonic
from uuid import UUID

import anyio
import httpx2

from notion_models import PageQuery, Record, RecordList
from settings import AssistantFailure, ROOT_PAGE_ID


def notion_transport() -> httpx2.AsyncHTTPTransport:
    return httpx2.AsyncHTTPTransport(
        http2=True, retries=3,
        limits=httpx2.Limits(max_connections=200, max_keepalive_connections=40, keepalive_expiry=30),
        socket_options=[(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)],
    )


class NotionReader:
    def __init__(self, http: httpx2.AsyncClient) -> None:
        self.http = http
        self.requests = 0
        self.last_request = 0.0
        self.warnings: list[str] = []
        self.ancestors: dict[UUID, Record] = {}

    async def read(self, path: str, query: PageQuery | None = None) -> bytes:
        if self.requests >= 120:
            raise AssistantFailure("notion_read_limit")
        children = path.endswith("/children")
        if query is not None and not children:
            if path != "search" and re.fullmatch(r"data_sources/[0-9a-f-]{36}/query", path) is None:
                raise AssistantFailure("notion_readonly_violation")
        elif re.fullmatch(r"(?:pages|blocks|databases|data_sources)/[0-9a-f-]{36}(?:/children)?", path) is None:
            raise AssistantFailure("notion_readonly_violation")
        delay = 0.35 - (monotonic() - self.last_request)
        if delay > 0:
            await anyio.sleep(delay)
        self.last_request = monotonic()
        self.requests += 1
        response = (
            await self.http.get(path, params=query.model_dump(mode="json", exclude_none=True) if query else None)
            if query is None or children
            else await self.http.post(path, json=query.model_dump(mode="json", exclude_none=True))
        )
        if response.status_code == 429:
            raise AssistantFailure("notion_http_429")
        if response.is_error:
            raise AssistantFailure(f"notion_http_{response.status_code}")
        return response.content

    async def metadata(self, kind: str, record_id: UUID) -> Record:
        record = self.ancestors.get(record_id)
        if record is None:
            record = Record.model_validate_json(await self.read(f"{kind}/{record_id}"))
            self.ancestors[record_id] = record
        return record

    async def in_scope(self, record: Record) -> bool:
        visited: set[UUID] = set()
        current = record
        for _ in range(40):
            if current.archived or current.in_trash:
                return False
            if current.id.hex == ROOT_PAGE_ID:
                return True
            if current.id in visited:
                return False
            visited.add(current.id)
            parent = current.parent
            pairs = (
                ("pages", parent.page_id), ("blocks", parent.block_id),
                ("databases", parent.database_id), ("data_sources", parent.data_source_id),
            )
            ancestor = next(((kind, record_id) for kind, record_id in pairs if record_id is not None), None)
            if ancestor is None:
                return False
            current = await self.metadata(*ancestor)
        raise AssistantFailure("notion_ancestry_limit")

    async def list_records(self, path: str, query: PageQuery) -> list[Record]:
        records: list[Record] = []
        cursor = query.start_cursor
        seen: set[UUID] = set()
        for _ in range(5):
            page_query = query.model_copy(update={"start_cursor": cursor})
            page = RecordList.model_validate_json(await self.read(path, page_query))
            records.extend(page.results)
            if not page.has_more:
                return records
            cursor = page.next_cursor
            if cursor is None or cursor in seen:
                raise AssistantFailure("notion_invalid_cursor")
            seen.add(cursor)
        self.warnings.append("페이지네이션이 조회당 500개 한도에 도달했어.")
        return records
