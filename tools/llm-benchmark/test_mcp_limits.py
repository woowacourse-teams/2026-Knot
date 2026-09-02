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

"""Tests for bounded live MCP pagination."""

from __future__ import annotations

import pytest
from mcp_adapter import LiveNotionMcpAdapter
from mcp_adapter_errors import McpAdapterError
from mcp_models import JsonObject, McpScope, McpToolResult
from mcp_transport import McpToolExchange


class _PagedClient:
    def __init__(self) -> None:
        self.calls: list[tuple[str, JsonObject]] = []

    def call_tool(self, name: str, arguments: JsonObject) -> McpToolExchange:
        self.calls.append((name, arguments))
        return McpToolExchange(
            McpToolResult.model_validate(
                {
                    "structuredContent": {
                        "results": [
                            {
                                "id": "page-1",
                                "url": "https://notion.so/page-1",
                                "title": "DB",
                                "snippet": "PostgreSQL",
                            }
                        ],
                        "has_more": True,
                        "next_cursor": "never-ending",
                    }
                }
            ),
            1,
            0,
            0,
            1.0,
        )


def test_live_search_rejects_pagination_beyond_the_configured_page_limit() -> None:
    # Given: a server that keeps returning a continuation cursor
    client = _PagedClient()
    adapter = LiveNotionMcpAdapter(
        client,
        McpScope("workspace-a", "snapshot-1", frozenset({"page-1"})),
        max_pages=1,
    )

    # When & then: the adapter fails closed instead of following unlimited pages
    with pytest.raises(McpAdapterError, match="page limit"):
        adapter.search("PostgreSQL", limit=2)
    assert len(client.calls) == 1
