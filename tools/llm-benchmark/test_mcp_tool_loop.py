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
    JsonObject,
    McpPage,
    McpScope,
    McpToolCallValidationError,
    NimFunctionCall,
    NimToolCall,
)
from mcp_tool_loop import (
    context_from_tool_executions,
    execute_nim_tool_calls,
    generate_with_mcp_tools,
)
from nim_client import ChatMessage, NimResult


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
            function=NimFunctionCall(name="notion-fetch", arguments='{"id":"page-1"}'),
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


def test_nim_tool_call_batch_has_a_bounded_number_of_operations() -> None:
    # Given: a model response that tries to fan out beyond the read budget
    calls = tuple(
        NimToolCall(
            id=f"search-{index}",
            function=NimFunctionCall(
                name="notion-search", arguments='{"query":"PostgreSQL"}'
            ),
        )
        for index in range(9)
    )

    # When & then: the batch is rejected before any adapter operation starts
    with pytest.raises(McpToolCallValidationError, match="maximum"):
        execute_nim_tool_calls(calls, _adapter(), _scope())


class _FakeToolCallingClient:
    def __init__(self, results: list[NimResult]) -> None:
        self.results = results
        self.requests: list[
            tuple[tuple[ChatMessage, ...], tuple[JsonObject, ...]]
        ] = []

    def generate(
        self,
        messages: tuple[ChatMessage, ...],
        *,
        tools: tuple[JsonObject, ...] = (),
    ) -> NimResult:
        self.requests.append((messages, tools))
        return self.results.pop(0)


def test_tool_calling_loop_executes_validated_mcp_results_and_resumes_generation() -> None:
    # Given: a model first requests a scoped search and then emits its grounded answer
    search_call = NimToolCall(
        id="search-1",
        function=NimFunctionCall(
            name="notion-search", arguments='{"query":"PostgreSQL"}'
        ),
    )
    client = _FakeToolCallingClient(
        [
            NimResult("", 1.0, 2.0, (search_call,)),
            NimResult("PostgreSQL을 사용합니다.", 3.0, 4.0),
        ]
    )

    # When: the backend runs the bounded MCP tool loop
    outcome = generate_with_mcp_tools(
        client,
        (ChatMessage(role="user", content="DB가 뭐야?"),),
        _adapter(),
        _scope(),
    )

    # Then: the result is grounded, correlated, and sent back as a tool message
    assert outcome.result.text == "PostgreSQL을 사용합니다."
    assert tuple(item.call_id for item in outcome.executions) == ("search-1",)
    assert len(client.requests) == 2
    assert client.requests[0][1][0]["function"]["name"] == "notion-search"
    resumed_messages = client.requests[1][0]
    assert resumed_messages[-1].role == "tool"
    assert resumed_messages[-1].tool_call_id == "search-1"
    assert "page-1" in resumed_messages[-1].content


def test_tool_executions_render_only_fetched_pages_as_grounding_context() -> None:
    # Given: one search result followed by one fetched page
    executions = execute_nim_tool_calls(
        (
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
        ),
        _adapter(),
        _scope(),
    )

    # When: the tool loop prepares context for the final answer
    context = context_from_tool_executions(executions)

    # Then: only fetched content is grounded and every actual call is accounted for
    assert context.retrieved_count == 1
    assert context.tool_calls == 2
    assert context.source_paths == ("https://notion.so/page-1",)
    assert "PostgreSQL 결정" in context.text
