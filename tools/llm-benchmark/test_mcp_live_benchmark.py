#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.14"
# dependencies = ["httpx2[http2,brotli,zstd]", "pydantic", "pydantic-settings", "pytest", "typer", "rich"]
# ///

# ─── How to run ───
# 1. Run without a network call:
#      uv run --with httpx2 --with pydantic --with pydantic-settings --with pytest --with typer --with rich pytest tools/llm-benchmark/test_mcp_live_benchmark.py
# ──────────────────

from __future__ import annotations

import json
from io import StringIO

import pytest
import typer
from benchmark_core import ContextPack
from benchmark_metadata import create_benchmark_metadata
from gold_set import BenchmarkCase
from mcp_adapter import McpContextTiming, ReplayMcpAdapter
from mcp_models import (
    JsonObject,
    McpPage,
    McpScope,
    McpToolTrace,
    NimFunctionCall,
    NimToolCall,
)
from nim_client import ChatMessage, NimResult, NimTransportError
from retrieval_policy import QueryPlan
from retrieval_policy import plan_query as real_plan_query
from run_mcp_live_benchmark import _record, _run_case, main
from typer.testing import CliRunner


def _timing() -> McpContextTiming:
    context = ContextPack(
        "## DB\nsource_path: https://notion.so/page-1\n\nPostgreSQL",
        ("https://notion.so/page-1",),
        1,
        2,
    )
    trace = McpToolTrace("search", "notion-search", 12.5, 2, 1, 1, 1)
    return McpContextTiming(context, (trace,))


def test_record_keeps_mcp_access_and_model_latency_separate() -> None:
    # Given: a measured MCP access phase and a separately measured model response
    record = _record(
        BenchmarkCase("G-001", "confirmed", "fact", ("DB",), "PostgreSQL", ("page-1",)),
        1,
        1,
        "DB",
        _timing(),
        12.5,
        "PostgreSQL",
        7.5,
        20.0,
        None,
    )

    # Then: end-to-end values add the phases exactly once
    assert record.search_ms == 12.5
    assert record.model_ttft_ms == 7.5
    assert record.model_total_ms == 20.0
    assert record.ttft_ms == 20.0
    assert record.total_ms == 32.5
    assert record.mcp_http_requests == 2
    assert record.mcp_page_count == 1
    assert record.mcp_retry_count == 1
    assert record.mcp_rate_limit_count == 1
    assert record.mcp_elapsed_ms == 12.5
    assert record.mcp_operations == ("search:notion-search",)


def test_record_keeps_live_run_metadata_with_the_observation() -> None:
    # Given: a live MCP execution identity without any credential field
    metadata = create_benchmark_metadata(
        run_id="live-001",
        phase="live",
        condition="cold",
        snapshot_id="notion-live",
        model="qwen/qwen3.6-27b",
        prompt="system prompt",
        generation_options={"tool_calling": True},
        observed_at="2026-09-02T09:00:00+00:00",
    )

    # When: the live result row is created
    record = _record(
        BenchmarkCase("G-001", "confirmed", "fact", ("DB",), "PostgreSQL", ("page-1",)),
        1,
        1,
        "DB",
        _timing(),
        12.5,
        "PostgreSQL",
        7.5,
        20.0,
        None,
        metadata=metadata,
    )

    # Then: the observation remains attributable to the live run
    assert record.metadata == metadata


def test_run_case_records_missing_chat_client_as_an_error() -> None:
    # Given: a matching scoped replay page but no answer generator
    page = McpPage(
        "page-1",
        "DB",
        "https://notion.so/page-1",
        "PostgreSQL",
        "workspace-a",
        "snapshot-1",
    )
    adapter = ReplayMcpAdapter(
        (page,), McpScope("workspace-a", "snapshot-1", frozenset({"page-1"}))
    )
    output = StringIO()
    case = BenchmarkCase(
        "G-001", "confirmed", "fact", ("PostgreSQL",), "PostgreSQL", ("page-1",)
    )

    # When: a non-retrieval-only run reaches generation without a chat client
    _run_case(output, adapter, None, case, 1, 1, False)

    # Then: it is an explicit failed observation, never an empty successful answer
    record = json.loads(output.getvalue())
    assert record["answer"] == ""
    assert record["error"] == str(
        NimTransportError("NIM client is required unless --retrieval-only is enabled")
    )


def test_live_runner_returns_a_cli_error_when_nim_configuration_is_missing(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # Given: a valid MCP credential and scope but no NIM API key or model
    monkeypatch.setenv("NOTION_MCP_ACCESS_TOKEN", "test-token")
    monkeypatch.setenv("NOTION_MCP_ENDPOINT_URL", "http://127.0.0.1:1/mcp")
    monkeypatch.delenv("NIM_API_KEY", raising=False)
    monkeypatch.delenv("NIM_MODEL", raising=False)
    app = typer.Typer()
    app.command()(main)

    # When: the normal live runner is invoked through its CLI
    result = CliRunner().invoke(
        app,
        [
            "--workspace-id",
            "workspace-a",
            "--allowed-page-ids",
            "page-1",
            "--case",
            "G-001",
        ],
    )

    # Then: configuration failure is actionable and does not expose a traceback
    assert result.exit_code == 2
    assert "NIM_API_KEY is required" in result.stdout
    assert "Traceback" not in result.stdout


def test_retrieval_only_runner_keeps_previous_questions_for_follow_ups(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # Given: a two-turn case executed without a chat generation client
    page = McpPage(
        "page-1",
        "DB",
        "https://notion.so/page-1",
        "PostgreSQL 결정 이유",
        "workspace-a",
        "snapshot-1",
    )
    adapter = ReplayMcpAdapter(
        (page,), McpScope("workspace-a", "snapshot-1", frozenset({"page-1"}))
    )
    observed_previous: list[tuple[str, ...]] = []

    def recording_plan(question: str, previous: tuple[str, ...]) -> QueryPlan:
        observed_previous.append(previous)
        return real_plan_query(question, previous)

    monkeypatch.setattr("run_mcp_live_benchmark.plan_query", recording_plan)
    output = StringIO()
    case = BenchmarkCase(
        "G-001",
        "confirmed",
        "follow_up",
        ("PostgreSQL", "그럼 이유는?"),
        "PostgreSQL 결정 이유",
        ("page-1",),
    )

    # When: both turns are measured as retrieval-only observations
    _run_case(output, adapter, None, case, 1, 1, True)

    # Then: the second query planner receives the first question as context
    assert observed_previous == [(), ("PostgreSQL",)]


class _FakeToolCallingClient:
    def __init__(self) -> None:
        self.results = [
            NimResult(
                "",
                1.0,
                2.0,
                (
                    NimToolCall(
                        id="search-1",
                        function=NimFunctionCall(
                            name="notion-search",
                            arguments='{"query":"DB"}',
                        ),
                    ),
                ),
            ),
            NimResult(
                "",
                2.0,
                3.0,
                (
                    NimToolCall(
                        id="fetch-1",
                        function=NimFunctionCall(
                            name="notion-fetch",
                            arguments='{"id":"page-1"}',
                        ),
                    ),
                ),
            ),
            NimResult("PostgreSQL을 사용합니다.", 3.0, 4.0),
        ]

    def generate(
        self,
        messages: tuple[ChatMessage, ...],
        *,
        tools: tuple[JsonObject, ...] = (),
    ) -> NimResult:
        return self.results.pop(0)


class _InvalidToolCallingClient:
    def generate(
        self,
        messages: tuple[ChatMessage, ...],
        *,
        tools: tuple[JsonObject, ...] = (),
    ) -> NimResult:
        return NimResult(
            "",
            1.0,
            2.0,
            (
                NimToolCall(
                    id="write-1",
                    function=NimFunctionCall(
                        name="notion-update", arguments="{}"
                    ),
                ),
            ),
        )


def test_live_runner_can_use_the_nim_mcp_tool_call_loop() -> None:
    # Given: a normal live run and a deterministic NIM client that searches then fetches
    page = McpPage(
        "page-1",
        "DB",
        "https://notion.so/page-1",
        "PostgreSQL 결정",
        "workspace-a",
        "snapshot-1",
    )
    scope = McpScope("workspace-a", "snapshot-1", frozenset({"page-1"}))
    adapter = ReplayMcpAdapter((page,), scope)
    output = StringIO()
    case = BenchmarkCase(
        "G-001", "confirmed", "fact", ("DB가 뭐야?",), "PostgreSQL", ("page-1",)
    )

    # When: the live runner enables model-driven MCP calls
    _run_case(
        output,
        adapter,
        _FakeToolCallingClient(),
        case,
        1,
        1,
        False,
        True,
        scope,
    )

    # Then: the final answer and fetched source are recorded together
    record = json.loads(output.getvalue())
    assert record["answer"] == "PostgreSQL을 사용합니다."
    assert record["source_paths"] == ["https://notion.so/page-1"]
    assert record["tool_calls"] == 2


def test_live_runner_does_not_count_model_generation_as_mcp_access() -> None:
    # Given: the model answers directly without requesting an MCP tool
    class DirectAnswerClient:
        def generate(
            self,
            messages: tuple[ChatMessage, ...],
            *,
            tools: tuple[JsonObject, ...] = (),
        ) -> NimResult:
            return NimResult("직접 답변", 10.0, 20.0)

    page = McpPage(
        "page-1",
        "DB",
        "https://notion.so/page-1",
        "PostgreSQL 결정",
        "workspace-a",
        "snapshot-1",
    )
    scope = McpScope("workspace-a", "snapshot-1", frozenset({"page-1"}))
    adapter = ReplayMcpAdapter((page,), scope)
    output = StringIO()
    case = BenchmarkCase(
        "G-001", "confirmed", "fact", ("DB가 뭐야?",), "PostgreSQL", ("page-1",)
    )

    # When: the live runner completes without any search/fetch operation
    _run_case(
        output,
        adapter,
        DirectAnswerClient(),
        case,
        1,
        1,
        False,
        True,
        scope,
    )

    # Then: end-to-end metrics contain model latency exactly once
    record = json.loads(output.getvalue())
    assert record["mcp_elapsed_ms"] == 0.0
    assert record["ttft_ms"] == pytest.approx(10.0)
    assert record["total_ms"] == pytest.approx(20.0)


def test_live_runner_passes_one_live_metadata_identity_to_each_observation() -> None:
    # Given: a scoped replay stand-in and a deterministic tool-calling client
    scope = McpScope("workspace-a", "snapshot-1", frozenset({"page-1"}))
    adapter = ReplayMcpAdapter(
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
        scope,
    )
    output = StringIO()
    case = BenchmarkCase(
        "G-001", "confirmed", "fact", ("DB가 뭐야?",), "PostgreSQL", ("page-1",)
    )
    metadata = create_benchmark_metadata(
        run_id="live-001",
        phase="live",
        condition="cold",
        snapshot_id="notion-live",
        model="qwen/qwen3.6-27b",
        prompt="system prompt",
        generation_options={"tool_calling": True},
        observed_at="2026-09-02T09:00:00+00:00",
    )

    # When: the runner receives a live run identity
    _run_case(
        output,
        adapter,
        _FakeToolCallingClient(),
        case,
        1,
        1,
        False,
        True,
        scope,
        metadata,
    )

    # Then: the serialized observation preserves the live identity
    record = json.loads(output.getvalue())
    assert record["metadata"]["run_id"] == "live-001"
    assert record["metadata"]["phase"] == "live"


def test_live_runner_records_invalid_model_tool_call_as_a_failed_observation() -> None:
    # Given: a model response that attempts a non-read-only MCP operation
    scope = McpScope("workspace-a", "snapshot-1", frozenset({"page-1"}))
    adapter = ReplayMcpAdapter((), scope)
    output = StringIO()
    case = BenchmarkCase(
        "G-001", "confirmed", "fact", ("DB가 뭐야?",), "PostgreSQL", ()
    )

    # When: the live runner processes the model-driven MCP request
    _run_case(
        output,
        adapter,
        _InvalidToolCallingClient(),
        case,
        1,
        1,
        False,
        True,
        scope,
    )

    # Then: the unsafe call is recorded as an error and never becomes an answer
    record = json.loads(output.getvalue())
    assert record["answer"] == ""
    assert "invalid MCP tool call" in record["error"]
