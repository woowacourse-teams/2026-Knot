#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.14"
# dependencies = ["numpy", "pydantic", "pytest", "typer"]
# ///

"""Tests for execution metadata shown in access reports."""

from __future__ import annotations

from analyze_access_benchmark import AccessRow, _metadata_summaries
from evaluate_rag_quality import EvaluationMetadata


def _row(metadata: EvaluationMetadata | None) -> AccessRow:
    return AccessRow(
        case_id="W-001",
        repeat=1,
        turn=1,
        strategy="rag",
        search_ms=10.0,
        metadata=metadata,
    )


def _metadata(phase: str, run_id: str) -> EvaluationMetadata:
    return EvaluationMetadata(
        run_id=run_id,
        phase=phase,
        condition="warm",
        snapshot_id="snapshot-001",
        model="qwen/qwen3.6-27b",
        prompt_sha256="0" * 64,
        generation_options={"temperature": 0.0},
        observed_at="2026-09-02T09:00:00+00:00",
    )


def test_access_report_separates_control_live_and_missing_metadata() -> None:
    # Given: observations from two phases plus one legacy row without metadata
    rows = (
        _row(_metadata("control", "control-001")),
        _row(_metadata("live", "live-001")),
        _row(None),
    )

    # When: the report summarizes the execution identities
    summaries = _metadata_summaries("검색 결과", rows)

    # Then: phase/run boundaries remain visible and missing identity is explicit
    assert [(item.phase, item.run_id, item.rows) for item in summaries] == [
        ("control", "control-001", 1),
        ("live", "live-001", 1),
        ("missing", "", 1),
    ]
