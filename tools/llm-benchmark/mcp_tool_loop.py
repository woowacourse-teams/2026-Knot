"""Deterministic orchestration for model-produced read-only MCP calls."""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Final, Protocol

from mcp_adapter import execute_validated_tool_call
from mcp_models import (
    JsonObject,
    McpFetchResult,
    McpReadAdapter,
    McpScope,
    McpSearchResult,
    McpToolCallValidationError,
    NimToolCall,
    validate_nim_tool_call,
)
from nim_client import ChatMessage, NimResult

MCP_TOOL_DEFINITIONS: Final[tuple[JsonObject, ...]] = (
    {
        "type": "function",
        "function": {
            "name": "notion-search",
            "description": "Search the connected Notion pages. Read-only.",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {"type": "string", "minLength": 1},
                },
                "required": ["query"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "notion-fetch",
            "description": "Fetch one connected Notion page. Read-only.",
            "parameters": {
                "type": "object",
                "properties": {
                    "id": {"type": "string"},
                    "url": {"type": "string"},
                    "cursor": {"type": "string"},
                },
                "anyOf": [
                    {"required": ["id"]},
                    {"required": ["url"]},
                ],
                "additionalProperties": False,
            },
        },
    },
)


class McpToolCallingClient(Protocol):
    """The NIM-compatible generation method required by the tool loop."""

    def generate(
        self,
        messages: tuple[ChatMessage, ...],
        *,
        tools: tuple[JsonObject, ...],
    ) -> NimResult:
        """Generate one answer or a batch of MCP tool calls."""
        ...


@dataclass(frozen=True, slots=True)
class McpToolExecution:
    """One validated model call paired with its scoped MCP result."""

    call_id: str
    result: McpSearchResult | McpFetchResult


@dataclass(frozen=True, slots=True)
class McpToolLoopResult:
    """Final model result plus every validated MCP execution in the loop."""

    result: NimResult
    executions: tuple[McpToolExecution, ...]
    rounds: int
    model_ttft_ms: float
    model_total_ms: float


def generate_with_mcp_tools(
    client: McpToolCallingClient,
    messages: tuple[ChatMessage, ...],
    adapter: McpReadAdapter,
    scope: McpScope,
    *,
    search_limit: int = 3,
    max_tool_calls: int = 8,
    max_rounds: int = 4,
) -> McpToolLoopResult:
    """Run NIM tool calls, feed normalized results back, and return the answer."""
    if max_rounds < 1:
        raise ValueError("max_rounds must be positive")
    conversation = list(messages)
    executions: list[McpToolExecution] = []
    first_ttft_ms: float | None = None
    model_total_ms = 0.0
    for round_number in range(1, max_rounds + 1):
        generated = client.generate(
            tuple(conversation),
            tools=MCP_TOOL_DEFINITIONS,
        )
        first_ttft_ms = (
            generated.ttft_ms if first_ttft_ms is None else first_ttft_ms
        )
        model_total_ms += generated.total_ms
        if not generated.tool_calls:
            return McpToolLoopResult(
                generated,
                tuple(executions),
                round_number,
                first_ttft_ms,
                model_total_ms,
            )
        batch = execute_nim_tool_calls(
            generated.tool_calls,
            adapter,
            scope,
            search_limit=search_limit,
            max_tool_calls=max_tool_calls,
        )
        executions.extend(batch)
        conversation.append(
            ChatMessage(
                role="assistant",
                content=generated.text,
                tool_calls=generated.tool_calls,
            )
        )
        conversation.extend(
            ChatMessage(
                role="tool",
                tool_call_id=execution.call_id,
                content=_tool_result_json(execution.result),
            )
            for execution in batch
        )
    raise McpToolCallValidationError(
        f"maximum MCP tool-call rounds is {max_rounds}"
    )


def execute_nim_tool_calls(
    calls: tuple[NimToolCall, ...],
    adapter: McpReadAdapter,
    scope: McpScope,
    *,
    search_limit: int = 3,
    max_tool_calls: int = 8,
) -> tuple[McpToolExecution, ...]:
    """Validate a complete model batch before executing it in model order."""
    if search_limit < 1:
        raise ValueError("search_limit must be positive")
    if max_tool_calls < 1:
        raise ValueError("max_tool_calls must be positive")
    if len(calls) > max_tool_calls:
        raise McpToolCallValidationError(
            f"maximum MCP tool calls per batch is {max_tool_calls}"
        )
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


def _tool_result_json(result: McpSearchResult | McpFetchResult) -> str:
    if isinstance(result, McpSearchResult):
        value = {
            "hits": [
                {
                    "page_id": hit.page_id,
                    "title": hit.title,
                    "url": hit.url,
                    "snippet": hit.snippet,
                    "last_edited_time": hit.last_edited_time,
                }
                for hit in result.hits
            ]
        }
    else:
        value = {
            "page": {
                "page_id": result.page.page_id,
                "title": result.page.title,
                "url": result.page.url,
                "content": result.page.content,
                "last_edited_time": result.page.last_edited_time,
            }
        }
    return json.dumps(value, ensure_ascii=False, sort_keys=True)
