"""Tests for canonical benchmark observation fingerprints."""

from __future__ import annotations

from benchmark_result_identity import observation_fingerprint


def test_observation_fingerprint_is_stable_for_the_same_observation() -> None:
    # Given: one benchmark result identity
    arguments = {
        "case_id": "W-001",
        "repeat": 1,
        "turn": 1,
        "strategy": "rag",
        "question": "질문",
        "answer": "답변",
        "source_paths": ("page-1",),
        "retrieved_count": 1,
        "error": None,
    }

    # Then: serializing the same identity is deterministic
    assert observation_fingerprint(**arguments) == observation_fingerprint(**arguments)


def test_observation_fingerprint_changes_when_answer_changes() -> None:
    # Given: two observations that differ in answer content
    common = {
        "case_id": "W-001",
        "repeat": 1,
        "turn": 1,
        "strategy": "rag",
        "question": "질문",
        "source_paths": ("page-1",),
        "retrieved_count": 1,
        "error": None,
    }

    # Then: a human label cannot be reused for a changed answer
    assert observation_fingerprint(
        answer="첫 답변", **common
    ) != observation_fingerprint(answer="바뀐 답변", **common)
