#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.14"
# dependencies = ["pydantic", "pydantic-settings", "pytest"]
# ///

"""Tests for deterministic, validated batches of NIM MCP tool calls."""

from __future__ import annotations

import pytest
from mcp_adapter import ReplayMcpAdapter
from mcp_models import (
    McpPage,
    McpScope,
    McpToolCallValidationError,
    NimFunctionCall,
    NimToolCall,
)
from mcp_tool_loop import execute_nim_tool_calls


def _scope() -> McpScope:
    return McpScope("workspace-a", "snapshot-1", frozenset({"page-1"}))


def _adapter() -> ReplayMcpAdapter:
    return ReplayMcpAdapter(
        (
            McpPage(
                "page-1",
                "DB",
                "https://notion.so/page-1",
                "PostgreSQL 결정",
                "workspace-a",
                "snapshot-1",
            ),
        ),
        _scope(),
    )


def test_nim_tool_calls_validate_before_executing_in_model_order() -> None:
    # Given: a model asks for a search followed by a detail read
    calls = (
        NimToolCall(
            id="search-1",
            function=NimFunctionCall(
                name="notion-search", arguments='{"query":"PostgreSQL"}'
            ),
        ),
        NimToolCall(
            id="fetch-1",
            function=NimFunctionCall(
                name="notion-fetch", arguments='{"id":"page-1"}'
            ),
        ),
    )

    # When: the batch crosses the single read-only execution boundary
    executions = execute_nim_tool_calls(calls, _adapter(), _scope())

    # Then: both results retain the model order and only scoped data is returned
    assert tuple(item.call_id for item in executions) == ("search-1", "fetch-1")
    assert executions[0].result.hits[0].page_id == "page-1"
    assert executions[1].result.page.content == "PostgreSQL 결정"


def test_invalid_nim_tool_call_prevents_every_call_in_the_batch() -> None:
    # Given: a valid read followed by an unsupported write-shaped tool
    calls = (
        NimToolCall(
            id="search-1",
            function=NimFunctionCall(
                name="notion-search", arguments='{"query":"PostgreSQL"}'
            ),
        ),
        NimToolCall(
            id="update-1",
            function=NimFunctionCall(name="notion-update", arguments="{}"),
        ),
    )

    # When & then: the whole batch is rejected before any adapter call can occur
    with pytest.raises(McpToolCallValidationError, match="not allowed"):
        execute_nim_tool_calls(calls, _adapter(), _scope())
