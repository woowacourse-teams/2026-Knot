"""Typed independent question workload for semantic benchmark review."""

from __future__ import annotations

from enum import StrEnum
from pathlib import Path

from pydantic import BaseModel, ConfigDict, Field, ValidationError, model_validator


class WorkloadCategory(StrEnum):
    """Question shapes required by the LLM search product contract."""

    FACT = "fact"
    DECISION_REASON = "decision_reason"
    DOCUMENT_LOCATION = "document_location"
    FOLLOW_UP = "follow_up"
    CONFLICT = "conflict"
    NO_ANSWER = "no_answer"
    BROAD = "broad"


class WorkloadError(Exception):
    """Raised when an independent workload is missing or malformed."""

    __slots__ = ("path", "reason")

    path: Path
    reason: str

    def __init__(self, path: Path, reason: str) -> None:
        super().__init__(reason)
        self.path = path
        self.reason = reason

    def __str__(self) -> str:
        return f"workload error at {self.path}: {self.reason}"


class WorkloadCase(BaseModel):
    """One independently sampled question or short follow-up conversation."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    case_id: str = Field(pattern=r"^W-[0-9]{3}$")
    category: WorkloadCategory
    turns: tuple[str, ...] = Field(min_length=1)
    expected_source_ids: tuple[str, ...] = Field(min_length=1, max_length=3)
    expected_facts: tuple[str, ...] = Field(min_length=1)
    independent: bool = True

    @model_validator(mode="after")
    def validate_turns(self) -> WorkloadCase:
        if any(not turn.strip() for turn in self.turns):
            raise ValueError("turns must not contain blank questions")
        if any(not source_id.strip() for source_id in self.expected_source_ids):
            raise ValueError("expected_source_ids must not contain blank IDs")
        if any(not fact.strip() for fact in self.expected_facts):
            raise ValueError("expected_facts must not contain blank facts")
        if not self.independent:
            raise ValueError("all workload cases must be marked independent")
        return self


class WorkloadManifest(BaseModel):
    """The complete versioned independent question workload."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    version: str = Field(min_length=1)
    cases: tuple[WorkloadCase, ...] = Field(min_length=1)

    @model_validator(mode="after")
    def reject_duplicate_ids(self) -> WorkloadManifest:
        identifiers = tuple(case.case_id for case in self.cases)
        if len(set(identifiers)) != len(identifiers):
            raise ValueError("duplicate case_id in workload")
        return self


def load_workload(path: Path, minimum_cases: int = 30) -> WorkloadManifest:
    """Load a JSON workload and enforce the independent-sample floor."""
    if minimum_cases < 1:
        raise ValueError("minimum_cases must be positive")
    try:
        raw = path.read_text(encoding="utf-8")
    except (FileNotFoundError, OSError, UnicodeDecodeError) as error:
        raise WorkloadError(path, str(error)) from error
    try:
        manifest = WorkloadManifest.model_validate_json(raw)
    except ValidationError as error:
        raise WorkloadError(path, "manifest validation failed") from error
    if len(manifest.cases) < minimum_cases:
        raise WorkloadError(
            path, f"expected at least {minimum_cases} cases, got {len(manifest.cases)}"
        )
    return manifest
