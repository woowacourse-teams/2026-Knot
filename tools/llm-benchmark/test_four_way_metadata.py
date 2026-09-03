#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.14"
# dependencies = [
#     "httpx2[http2,brotli,zstd]",
#     "pgvector",
#     "psycopg[binary]",
#     "pydantic",
#     "pydantic-settings",
#     "pytest",
#     "rich",
#     "typer",
# ]
# ///

"""Tests that the four-way runner carries one run identity into every row."""

from __future__ import annotations

from access_context import AccessContextTiming
from benchmark_core import ContextPack
from benchmark_metadata import create_benchmark_metadata
from gold_set import BenchmarkCase
from multi_schedule import MultiTrial
from run_four_way_benchmark import _record, _select_cases


def test_four_way_record_keeps_the_control_run_identity() -> None:
    # Given: one control run metadata object and a benchmark observation
    metadata = create_benchmark_metadata(
        run_id="control-001",
        phase="control",
        condition="warm",
        snapshot_id="snapshot-001",
        model="qwen/qwen3.6-27b",
        prompt="system prompt",
        generation_options={"temperature": 0.0},
        observed_at="2026-09-02T09:00:00+00:00",
    )
    record = _record(
        BenchmarkCase("W-001", "confirmed", "fact", ("질문",), "답", ("page-1",)),
        MultiTrial("W-001", 1, ("rag",)),
        1,
        "rag",
        "질문",
        ContextPack("근거", ("page-1",), 1, 0),
        10.0,
        AccessContextTiming(ContextPack("근거", ("page-1",), 1, 0), 2.0, 3.0),
        "답변",
        4.0,
        5.0,
        None,
        metadata,
    )

    # Then: the JSONL row can be grouped with the exact control run
    assert record.metadata == metadata


def test_four_way_runner_selects_every_independent_workload_case_by_default() -> None:
    # Given: the independent 30+ workload is selected without a manual case list
    cases = tuple(
        BenchmarkCase(
            f"W-{index:03d}",
            "needs-human",
            "fact",
            (f"질문 {index}",),
            "답",
            (f"page-{index}",),
        )
        for index in range(1, 32)
    )

    # When: the four-way runner resolves its default case selection
    selected = _select_cases(cases, None)

    # Then: no independent question is silently dropped from the evaluation
    assert tuple(case.case_id for case in selected) == tuple(
        f"W-{index:03d}" for index in range(1, 32)
    )
