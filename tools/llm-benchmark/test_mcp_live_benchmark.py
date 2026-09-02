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
from gold_set import BenchmarkCase
from mcp_adapter import McpContextTiming, ReplayMcpAdapter
from mcp_models import McpPage, McpScope, McpToolTrace
from nim_client import NimTransportError
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
