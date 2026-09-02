"""Reproducibility metadata shared by benchmark runners and reports."""

from __future__ import annotations

import hashlib
import json
import re
from collections.abc import Iterable, Mapping
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Literal
from uuid import uuid4

from benchmark_core import Document

RunPhase = Literal["control", "live"]
RunCondition = Literal["cold", "warm"]
MetadataScalar = str | int | float | bool | None
SnapshotEntry = Document | tuple[str, str, str]
_SENSITIVE_OPTION_TERMS = frozenset(
    {"access", "credential", "key", "password", "secret", "token"}
)


@dataclass(frozen=True, slots=True)
class BenchmarkMetadata:
    """Inputs that make one benchmark observation comparable and auditable."""

    run_id: str
    phase: RunPhase
    condition: RunCondition
    snapshot_id: str
    model: str
    prompt_sha256: str
    generation_options: dict[str, MetadataScalar]
    observed_at: str


def create_benchmark_metadata(
    *,
    run_id: str,
    phase: str,
    condition: str,
    snapshot_id: str,
    model: str,
    prompt: str,
    generation_options: Mapping[str, MetadataScalar],
    observed_at: str | None = None,
) -> BenchmarkMetadata:
    """Create validated run metadata without accepting credential-shaped options."""
    normalized_phase = _dimension(phase, ("control", "live"), "phase")
    normalized_condition = _dimension(condition, ("cold", "warm"), "condition")
    normalized_snapshot = _required(snapshot_id, "snapshot_id")
    normalized_model = _required(model, "model")
    normalized_prompt = _required(prompt, "prompt")
    options = _copy_options(generation_options)
    return BenchmarkMetadata(
        _required(run_id, "run_id") if run_id.strip() else f"run-{uuid4().hex}",
        normalized_phase,  # type: ignore[arg-type]
        normalized_condition,  # type: ignore[arg-type]
        normalized_snapshot,
        normalized_model,
        hashlib.sha256(normalized_prompt.encode("utf-8")).hexdigest(),
        options,
        observed_at or datetime.now(timezone.utc).isoformat(),
    )


def snapshot_fingerprint(documents: Iterable[SnapshotEntry]) -> str:
    """Hash normalized path, title, and content so a snapshot has a stable identity."""
    entries = sorted(
        (_snapshot_fields(document) for document in documents),
        key=lambda entry: entry[0],
    )
    encoded = json.dumps(
        entries, ensure_ascii=False, sort_keys=False, separators=(",", ":")
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _snapshot_fields(document: SnapshotEntry) -> tuple[str, str, str]:
    if isinstance(document, Document):
        return str(document.path), document.title, document.content
    path, title, content = document
    return path, title, content


def _copy_options(
    options: Mapping[str, MetadataScalar],
) -> dict[str, MetadataScalar]:
    copied: dict[str, MetadataScalar] = {}
    for key, value in sorted(options.items()):
        normalized_key = key.strip()
        if not normalized_key:
            raise ValueError("generation option names must not be blank")
        option_parts = re.split(r"[^a-z0-9]+", normalized_key.casefold())
        if any(part in _SENSITIVE_OPTION_TERMS for part in option_parts):
            raise ValueError("generation options must not contain credentials")
        copied[normalized_key] = value
    return copied


def _dimension(value: str, allowed: tuple[str, ...], field: str) -> str:
    normalized = value.strip()
    if normalized not in allowed:
        choices = ", ".join(allowed)
        raise ValueError(f"{field} must be one of: {choices}")
    return normalized


def _required(value: str, field: str) -> str:
    normalized = value.strip()
    if not normalized:
        raise ValueError(f"{field} must not be blank")
    return normalized
