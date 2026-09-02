#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.14"
# dependencies = ["numpy", "pydantic", "pytest", "typer"]
# ///

"""Tests for access-report workload selection."""

from __future__ import annotations

from analyze_access_benchmark import _expected_sources
from gold_set import BenchmarkCase


def test_access_report_uses_the_selected_independent_workload_sources() -> None:
    # Given: a workload whose expected source IDs differ from the legacy G-series
    cases = (
        BenchmarkCase(
            "W-001",
            "needs-human",
            "fact",
            ("질문",),
            "답",
            ("page-1", "page-2"),
        ),
    )

    # When: the access analyzer builds its source expectations
    expected = _expected_sources(cases)

    # Then: report quality is evaluated against the selected workload, not a hardcoded gold set
    assert expected == {"W-001": {"page1", "page2"}}
