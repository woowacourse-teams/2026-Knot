"""Deterministic orchestration for model-produced read-only MCP calls."""

from __future__ import annotations

from dataclasses import dataclass

from mcp_adapter import execute_validated_tool_call
from mcp_models import (
    McpFetchResult,
    McpReadAdapter,
    McpScope,
    McpSearchResult,
    NimToolCall,
    validate_nim_tool_call,
)


@dataclass(frozen=True, slots=True)
class McpToolExecution:
    """One validated model call paired with its scoped MCP result."""

    call_id: str
    result: McpSearchResult | McpFetchResult


def execute_nim_tool_calls(
    calls: tuple[NimToolCall, ...],
    adapter: McpReadAdapter,
    scope: McpScope,
    *,
    search_limit: int = 3,
) -> tuple[McpToolExecution, ...]:
    """Validate a complete model batch before executing it in model order."""
    if search_limit < 1:
        raise ValueError("search_limit must be positive")
    validated = tuple(validate_nim_tool_call(call) for call in calls)
    return tuple(
        McpToolExecution(
            call.call_id,
            execute_validated_tool_call(
                call, adapter, scope, search_limit=search_limit
            ),
        )
        for call in validated
    )
