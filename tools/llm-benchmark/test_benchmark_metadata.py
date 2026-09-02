#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.14"
# dependencies = ["pytest"]
# ///

"""Tests for reproducible benchmark run metadata."""

from __future__ import annotations

import pytest

from benchmark_metadata import (
    create_benchmark_metadata,
    snapshot_fingerprint,
)


def test_metadata_records_reproducibility_inputs_without_secrets() -> None:
    # Given: one controlled snapshot and fixed generation options
    metadata = create_benchmark_metadata(
        run_id="run-001",
        phase="control",
        condition="warm",
        snapshot_id="snapshot-001",
        model="qwen/qwen3.6-27b",
        prompt="system prompt",
        generation_options={
            "temperature": 0.0,
            "max_tokens": 4096,
            "reasoning_effort": "none",
        },
        observed_at="2026-09-02T09:00:00+00:00",
    )

    # Then: the run identity and prompt/options fingerprints are explicit
    assert metadata.run_id == "run-001"
    assert metadata.phase == "control"
    assert metadata.condition == "warm"
    assert metadata.snapshot_id == "snapshot-001"
    assert metadata.model == "qwen/qwen3.6-27b"
    assert len(metadata.prompt_sha256) == 64
    assert metadata.generation_options == {
        "max_tokens": 4096,
        "reasoning_effort": "none",
        "temperature": 0.0,
    }
    assert metadata.observed_at == "2026-09-02T09:00:00+00:00"


def test_snapshot_fingerprint_is_stable_for_document_order() -> None:
    # Given: the same snapshot represented in different traversal orders
    documents = (
        ("b.md", "B", "second"),
        ("a.md", "A", "first"),
    )

    # Then: path ordering does not change the snapshot identity
    assert snapshot_fingerprint(documents) == snapshot_fingerprint(tuple(reversed(documents)))


@pytest.mark.parametrize(
    ("field", "value"),
    (("phase", "unknown"), ("condition", "lukewarm")),
)
def test_metadata_rejects_unknown_run_dimensions(field: str, value: str) -> None:
    # Given: an invalid control/live or cold/warm label
    arguments = {
        "run_id": "run-001",
        "phase": "control",
        "condition": "warm",
        "snapshot_id": "snapshot-001",
        "model": "model",
        "prompt": "prompt",
        "generation_options": {},
    }
    arguments[field] = value

    # When & then: incomparable observations cannot be created
    with pytest.raises(ValueError):
        create_benchmark_metadata(**arguments)
