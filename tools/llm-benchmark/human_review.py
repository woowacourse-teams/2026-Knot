"""Typed human-quality labels for benchmark answers and sources."""

from __future__ import annotations

import json
from collections import Counter
from enum import StrEnum
from pathlib import Path

from pydantic import BaseModel, ConfigDict, Field, ValidationError, model_validator

ReviewKey = tuple[str, int, int, str]


class HumanReviewStatus(StrEnum):
    """Aggregate state of a human review workload."""

    PASS = "pass"
    FAIL = "fail"
    PENDING = "pending"
    NOT_EVALUATED = "not_evaluated"


class HumanReviewDecision(StrEnum):
    """Allowed terminal or pending decision values for one review row."""

    PASS = "pass"
    FAIL = "fail"
    PENDING = "pending"


class HumanReviewError(Exception):
    """Raised when a human review file cannot be loaded."""

    __slots__ = ("path", "reason")

    path: Path
    reason: str

    def __init__(self, path: Path, reason: str) -> None:
        super().__init__(reason)
        self.path = path
        self.reason = reason

    def __str__(self) -> str:
        return f"human review error at {self.path}: {self.reason}"


class HumanReviewRow(BaseModel):
    """One reviewer decision for one generated benchmark answer."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    case_id: str = Field(min_length=1)
    repeat: int = Field(ge=1)
    turn: int = Field(ge=1)
    strategy: str = Field(min_length=1)
    decision: HumanReviewDecision = HumanReviewDecision.PENDING
    answer_correct: bool | None = None
    sources_relevant: bool | None = None
    policy_compliant: bool | None = None
    reviewer: str = ""
    notes: str = ""
    result_fingerprint: str = ""

    @model_validator(mode="after")
    def validate_decision_evidence(self) -> HumanReviewRow:
        if self.decision is HumanReviewDecision.PENDING:
            return self
        if not self.reviewer.strip():
            raise ValueError("terminal human review requires reviewer")
        if not self.result_fingerprint.strip():
            raise ValueError("terminal human review requires result_fingerprint")
        dimensions = (self.answer_correct, self.sources_relevant, self.policy_compliant)
        if any(value is None for value in dimensions):
            raise ValueError("terminal human review requires every quality dimension")
        passed = all(value is True for value in dimensions)
        if self.decision is HumanReviewDecision.PASS and not passed:
            raise ValueError("pass review requires every quality dimension to be true")
        if self.decision is HumanReviewDecision.FAIL and passed:
            raise ValueError(
                "fail review requires at least one false quality dimension"
            )
        return self

    @property
    def key(self) -> ReviewKey:
        """Return the stable observation identity used for coverage checks."""
        return (self.case_id, self.repeat, self.turn, self.strategy)


class HumanReviewSummary(BaseModel):
    """Coverage and decision summary for the human-quality gate."""

    model_config = ConfigDict(frozen=True)

    expected: int
    received: int
    passed: int
    failed: int
    pending: int
    missing: tuple[ReviewKey, ...]
    duplicates: tuple[ReviewKey, ...]
    unexpected: tuple[ReviewKey, ...]
    invalid: tuple[ReviewKey, ...] = ()

    @property
    def status(self) -> HumanReviewStatus:
        if self.received == 0:
            return HumanReviewStatus.NOT_EVALUATED
        if self.failed or self.duplicates or self.unexpected or self.invalid:
            return HumanReviewStatus.FAIL
        if self.pending or self.missing:
            return HumanReviewStatus.PENDING
        return (
            HumanReviewStatus.PASS
            if self.expected > 0 and self.passed == self.expected
            else HumanReviewStatus.FAIL
        )

    @property
    def gate_passed(self) -> bool:
        return self.status is HumanReviewStatus.PASS


def evaluate_human_review(
    rows: tuple[HumanReviewRow, ...],
    expected_keys: frozenset[ReviewKey],
    invalid_keys: frozenset[ReviewKey] = frozenset(),
) -> HumanReviewSummary:
    """Require exactly one completed, internally consistent label per answer."""
    counts = Counter(row.key for row in rows)
    observed = frozenset(counts)
    duplicates = tuple(sorted(key for key, count in counts.items() if count > 1))
    missing = tuple(sorted(expected_keys - observed))
    unexpected = tuple(sorted(observed - expected_keys))
    return HumanReviewSummary(
        expected=len(expected_keys),
        received=len(rows),
        passed=sum(row.decision is HumanReviewDecision.PASS for row in rows),
        failed=sum(row.decision is HumanReviewDecision.FAIL for row in rows),
        pending=sum(row.decision is HumanReviewDecision.PENDING for row in rows),
        missing=missing,
        duplicates=duplicates,
        unexpected=unexpected,
        invalid=tuple(sorted(invalid_keys)),
    )


def load_human_review(path: Path) -> tuple[HumanReviewRow, ...]:
    """Load one JSONL file without accepting malformed or partially typed labels."""
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except (FileNotFoundError, OSError, UnicodeDecodeError) as error:
        raise HumanReviewError(path, str(error)) from error
    rows: list[HumanReviewRow] = []
    for line_number, line in enumerate(lines, start=1):
        if not line.strip():
            continue
        try:
            rows.append(HumanReviewRow.model_validate_json(line))
        except (ValidationError, json.JSONDecodeError) as error:
            raise HumanReviewError(
                path, f"invalid row at line {line_number}"
            ) from error
    return tuple(rows)


def expected_review_keys(
    cases: tuple[tuple[str, int], ...],
    strategy: str,
    repeat: int = 1,
) -> frozenset[ReviewKey]:
    """Build the exact review coverage expected for a case/turn workload."""
    if repeat < 1:
        raise ValueError("repeat must be positive")
    if not strategy.strip():
        raise ValueError("strategy must not be blank")
    return frozenset((case_id, repeat, turn, strategy) for case_id, turn in cases)
