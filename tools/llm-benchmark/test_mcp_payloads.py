#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.14"
# dependencies = [
#     "httpx2[http2,brotli,zstd]",
#     "pydantic",
#     "pydantic-settings",
#     "pytest",
# ]
# ///

"""Tests for the JSON-in-text payload shape returned by Notion MCP."""

from __future__ import annotations

import json

from mcp_adapter import LiveNotionMcpAdapter
from mcp_models import JsonObject, McpScope, McpToolResult
from mcp_transport import McpToolExchange


class _TextPayloadClient:
    def __init__(self, results: list[McpToolExchange]) -> None:
        self._results = results
        self.calls: list[tuple[str, JsonObject]] = []

    def call_tool(self, name: str, arguments: JsonObject) -> McpToolExchange:
        self.calls.append((name, arguments))
        return self._results.pop(0)


def _exchange(payload: JsonObject) -> McpToolExchange:
    return McpToolExchange(
        McpToolResult.model_validate(
            {"content": [{"type": "text", "text": json.dumps(payload)}]}
        ),
        1,
        0,
        0,
        1.0,
    )


def test_live_adapter_reads_notion_json_from_text_content_blocks() -> None:
    # Given: the hosted MCP shape where search and fetch payloads are JSON text
    client = _TextPayloadClient(
        [
            _exchange(
                {
                    "results": [
                        {
                            "id": "page-1",
                            "url": "https://notion.so/page-1",
                            "title": "DB",
                            "snippet": "PostgreSQL",
                            "workspace_id": "workspace-a",
                            "snapshot_id": "snapshot-1",
                        }
                    ]
                }
            ),
            _exchange(
                {
                    "id": "page-1",
                    "title": "DB",
                    "url": "https://notion.so/page-1",
                    "content": "PostgreSQL 결정",
                    "workspace_id": "workspace-a",
                    "snapshot_id": "snapshot-1",
                }
            ),
        ]
    )
    adapter = LiveNotionMcpAdapter(
        client,
        McpScope("workspace-a", "snapshot-1", frozenset({"page-1"})),
    )

    # When: the adapter normalizes both MCP operations
    search = adapter.search("PostgreSQL", limit=1)
    page = adapter.fetch(search.hits[0]).page

    # Then: JSON text is parsed into the same scoped page contract
    assert search.hits[0].page_id == "page-1"
    assert page.title == "DB"
    assert page.content == "PostgreSQL 결정"
