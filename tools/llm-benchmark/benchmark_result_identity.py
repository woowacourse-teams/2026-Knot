"""Canonical identities for benchmark observations and human review labels."""

from __future__ import annotations

import hashlib
import json


def observation_fingerprint(
    *,
    case_id: str,
    repeat: int,
    turn: int,
    strategy: str,
    question: str,
    answer: str,
    source_paths: tuple[str, ...],
    retrieved_count: int,
    error: str | None,
) -> str:
    """Return a stable SHA-256 identity for one generated observation."""
    payload = {
        "answer": answer,
        "case_id": case_id,
        "error": error,
        "question": question,
        "repeat": repeat,
        "retrieved_count": retrieved_count,
        "source_paths": source_paths,
        "strategy": strategy,
        "turn": turn,
    }
    encoded = json.dumps(
        payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()
