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

"""Security regression tests for the MCP page and context boundaries."""

from __future__ import annotations

import pytest
from mcp_adapter import ReplayMcpAdapter, execute_validated_tool_call
from mcp_adapter_errors import McpScopeError
from mcp_adapter_support import render_context
from mcp_models import (
    McpPage,
    McpScope,
    NimFunctionCall,
    NimToolCall,
    validate_nim_tool_call,
)
from mcp_parsing import is_safe_notion_url


def _scope() -> McpScope:
    return McpScope("workspace-a", "snapshot-1", frozenset({"page-1"}))


def _adapter() -> ReplayMcpAdapter:
    return ReplayMcpAdapter(
        (
            McpPage(
                "page-1",
                "DB",
                "https://www.notion.so/page-1",
                "PostgreSQL 결정",
                "workspace-a",
                "snapshot-1",
            ),
        ),
        _scope(),
    )


def test_notion_url_rejects_query_and_fragment_data() -> None:
    # Given: a Notion-looking URL carrying data outside the page path
    # When & then: only the canonical page URL is safe to pass downstream
    assert is_safe_notion_url("https://www.notion.so/page-1")
    assert not is_safe_notion_url("https://www.notion.so/page-1?access_token=secret")
    assert not is_safe_notion_url("https://www.notion.so/page-1#secret")


def test_validated_fetch_rejects_a_url_for_a_different_page_id() -> None:
    # Given: a model binds an allowed page ID to another allowed-looking Notion URL
    call = validate_nim_tool_call(
        NimToolCall(
            id="fetch-1",
            function=NimFunctionCall(
                name="notion-fetch",
                arguments='{"id":"page-1","url":"https://www.notion.so/page-2"}',
            ),
        )
    )

    # When & then: the identity mismatch is rejected before the adapter is called
    with pytest.raises(McpScopeError, match="identity"):
        execute_validated_tool_call(call, _adapter(), _scope())


def test_rendered_context_preserves_the_exact_source_url() -> None:
    # Given: a fetched page whose source is a URL rather than a filesystem path
    page = McpPage(
        "page-1",
        "DB",
        "https://www.notion.so/page-1",
        "PostgreSQL 결정",
        "workspace-a",
        "snapshot-1",
    )

    # When: the page is rendered for the answer generator
    context = render_context((page,))

    # Then: the model receives a clickable, unmodified source URL
    assert "source_path: https://www.notion.so/page-1" in context.text
    assert "source_path: https:/www.notion.so/page-1" not in context.text
