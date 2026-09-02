#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.14"
# dependencies = ["pydantic", "typer"]
# ///

"""Evaluate RAG retrieval policy and answer-shape gates against the gold set."""

# ruff: noqa: B008  # Typer uses Option calls as the CLI declaration syntax.

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

import typer
from answer_quality_policy import answer_shape_passes
from gold_set import BenchmarkCase, load_cases
from human_review import (
    HumanReviewError,
    HumanReviewRow,
    HumanReviewSummary,
    evaluate_human_review,
    expected_review_keys,
    load_human_review,
)
from pydantic import BaseModel, ConfigDict, ValidationError

_DEFAULT_RESULTS = Path(".benchmark-data/rag-quality-retrieval-final-10x.jsonl")
_DEFAULT_GOLD_SET = Path("docs/llm-search-benchmark-gold-set.md")
_NO_ANSWER_CASES = frozenset({"G-011", "G-012"})
_NO_ANSWER_CATEGORIES = frozenset({"no_answer", "broad", "clarification"})


class EvaluationError(Exception):
    """Raised when a result file cannot be evaluated."""

    __slots__ = ("reason",)

    reason: str

    def __init__(self, reason: str) -> None:
        super().__init__(reason)
        self.reason = reason

    def __str__(self) -> str:
        return f"RAG evaluation error: {self.reason}"


class EvaluationRow(BaseModel):
    """Fields required from a benchmark JSONL observation."""

    model_config = ConfigDict(extra="ignore", frozen=True)

    case_id: str
    repeat: int
    turn: int
    strategy: str
    answer: str = ""
    source_paths: tuple[str, ...] = ()
    retrieved_count: int = 0
    error: str | None = None


@dataclass(frozen=True, slots=True)
class TurnEvaluation:
    """One result row's retrieval and optional answer checks."""

    key: tuple[str, int, int]
    retrieval_passed: bool
    answer_passed: bool | None
    reasons: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class EvaluationSummary:
    """Aggregate gate result for one result file."""

    rows: int
    expected_rows: int
    retrieval_passed: int
    answer_evaluated: int
    answer_passed: int
    retrieval_failures: tuple[str, ...]
    answer_failures: tuple[str, ...]

    @property
    def retrieval_gate_passed(self) -> bool:
        return self.rows == self.expected_rows and not self.retrieval_failures

    @property
    def answer_gate_passed(self) -> bool | None:
        if self.answer_evaluated != self.expected_rows:
            return None
        return self.answer_passed == self.expected_rows and not self.answer_failures


def main(
    results: list[Path] = typer.Option(
        [], "--results", help="One or more JSONL outputs from the RAG benchmark."
    ),
    gold_set: Path = typer.Option(_DEFAULT_GOLD_SET, help="Markdown gold-set path."),
    strategy: str = typer.Option("rag", help="Expected strategy label."),
    repeats: int = typer.Option(10, min=1, help="Expected repeats per case."),
    require_answer: bool = typer.Option(
        False, help="Fail when an answer is missing or fails the answer-shape gate."
    ),
    human_labels: Path | None = typer.Option(
        None, "--human-labels", help="Optional JSONL human-review labels."
    ),
    human_repeat: int = typer.Option(
        1, min=1, help="Result repeat represented by the human-review labels."
    ),
    require_human_review: bool = typer.Option(
        False, help="Fail unless every expected answer has a terminal human label."
    ),
) -> None:
    """Check every expected case/turn/repeat and print a CI-friendly gate summary."""
    cases = load_cases(gold_set)
    result_paths = tuple(results) if results else (_DEFAULT_RESULTS,)
    rows = tuple(row for path in result_paths for row in _load_rows(path))
    summary = evaluate(rows, cases, strategy, repeats)
    human_summary = evaluate_human_labels(
        () if human_labels is None else load_human_review(human_labels),
        cases,
        strategy,
        repeat=human_repeat,
    )
    typer.echo(f"results={summary.rows} expected={summary.expected_rows}")
    typer.echo(
        "retrieval_gate="
        f"{'PASS' if summary.retrieval_gate_passed else 'FAIL'} "
        f"({summary.retrieval_passed}/{summary.expected_rows})"
    )
    if summary.answer_gate_passed is None:
        status = "NOT_EVALUATED" if summary.answer_evaluated == 0 else "PARTIAL"
        typer.echo(
            f"answer_gate={status} ({summary.answer_passed}/{summary.answer_evaluated})"
        )
    else:
        typer.echo(
            "answer_gate="
            f"{'PASS' if summary.answer_gate_passed else 'FAIL'} "
            f"({summary.answer_passed}/{summary.answer_evaluated})"
        )
    for failure in summary.retrieval_failures:
        typer.echo(f"FAIL: {failure}")
    for failure in summary.answer_failures:
        typer.echo(f"ANSWER FAIL: {failure}")
    if summary.answer_evaluated < summary.expected_rows:
        typer.echo(
            f"answer coverage={summary.answer_evaluated}/{summary.expected_rows} "
            "(retrieval-only output is intentionally partial)"
        )
    typer.echo(
        f"human_gate={human_summary.status.name} "
        f"({human_summary.passed}/{human_summary.expected})"
    )
    for issue in (
        *human_summary.missing,
        *human_summary.duplicates,
        *human_summary.unexpected,
    ):
        typer.echo(f"HUMAN REVIEW COVERAGE: {issue}")
    if not summary.retrieval_gate_passed or (
        require_answer and summary.answer_gate_passed is not True
    ):
        raise typer.Exit(code=1)
    if require_human_review and not human_summary.gate_passed:
        raise typer.Exit(code=1)


def evaluate(
    rows: tuple[EvaluationRow, ...],
    cases: tuple[BenchmarkCase, ...],
    strategy: str,
    repeats: int = 10,
) -> EvaluationSummary:
    """Evaluate structural, source, abstention, and optional answer-shape gates."""
    if repeats < 1:
        raise ValueError("repeats must be positive")
    case_by_id = {case.case_id: case for case in cases}
    expected_keys = {
        (case.case_id, repeat, turn)
        for case in cases
        for repeat in range(1, repeats + 1)
        for turn in range(1, len(case.turns) + 1)
    }
    by_key: dict[tuple[str, int, int], EvaluationRow] = {}
    retrieval_failures: list[str] = []
    for row in rows:
        key = (row.case_id, row.repeat, row.turn)
        if row.strategy != strategy:
            retrieval_failures.append(
                f"{key}: strategy={row.strategy!r}, expected={strategy!r}"
            )
        if key in by_key:
            retrieval_failures.append(f"{key}: duplicate result row")
        by_key[key] = row
    missing = sorted(expected_keys - by_key.keys())
    retrieval_failures.extend(f"{key}: missing result row" for key in missing)
    unexpected = sorted(by_key.keys() - expected_keys)
    retrieval_failures.extend(f"{key}: unexpected result row" for key in unexpected)

    evaluations: list[TurnEvaluation] = []
    answer_failures: list[str] = []
    for key, row in sorted(by_key.items()):
        case = case_by_id.get(row.case_id)
        if case is None or key not in expected_keys:
            continue
        reasons = list(_structural_failures(row))
        expected_sources = _expected_sources(case, row.turn)
        if not _source_match(
            row.source_paths, expected_sources, case.case_id, row.turn
        ):
            reasons.append(
                f"expected sources={expected_sources!r}, actual={row.source_paths!r}"
            )
        if row.case_id == "G-011" and row.source_paths:
            reasons.append("no-answer case returned a source")
        if row.case_id == "G-012" and row.source_paths:
            reasons.append("clarification case returned a source")
        answer_passed = (
            None
            if not row.answer.strip()
            else answer_shape_passes(row.answer, case.case_id, row.turn)
        )
        if answer_passed is False:
            answer_failures.append(
                f"{key}: answer does not satisfy the gold-set policy shape"
            )
        evaluations.append(
            TurnEvaluation(key, not reasons, answer_passed, tuple(reasons))
        )
        retrieval_failures.extend(f"{key}: {reason}" for reason in reasons)

    answer_evaluated = sum(item.answer_passed is not None for item in evaluations)
    answer_passed = sum(item.answer_passed is True for item in evaluations)
    retrieval_passed = sum(item.retrieval_passed for item in evaluations)
    return EvaluationSummary(
        len(rows),
        len(expected_keys),
        retrieval_passed,
        answer_evaluated,
        answer_passed,
        tuple(dict.fromkeys(retrieval_failures)),
        tuple(dict.fromkeys(answer_failures)),
    )


def evaluate_human_labels(
    rows: tuple[HumanReviewRow, ...],
    cases: tuple[BenchmarkCase, ...],
    strategy: str,
    repeat: int = 1,
) -> HumanReviewSummary:
    """Evaluate one human label for every turn in one selected result repeat."""
    case_turns = tuple(
        (case.case_id, turn) for case in cases for turn in range(1, len(case.turns) + 1)
    )
    return evaluate_human_review(
        rows, expected_review_keys(case_turns, strategy, repeat)
    )


def _load_rows(path: Path) -> tuple[EvaluationRow, ...]:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except (FileNotFoundError, OSError, UnicodeDecodeError) as error:
        raise EvaluationError(str(error)) from error
    rows: list[EvaluationRow] = []
    for line_number, line in enumerate(lines, start=1):
        if not line.strip():
            continue
        try:
            rows.append(EvaluationRow.model_validate_json(line))
        except (ValidationError, json.JSONDecodeError) as error:
            raise EvaluationError(f"invalid record at line {line_number}") from error
    if not rows:
        raise EvaluationError("no records found")
    return tuple(rows)


def _structural_failures(row: EvaluationRow) -> tuple[str, ...]:
    failures: list[str] = []
    if row.error:
        failures.append(f"benchmark error={row.error}")
    if row.retrieved_count != len(row.source_paths):
        failures.append("retrieved_count does not equal source_paths length")
    if row.retrieved_count > 3:
        failures.append("more than three source documents returned")
    if len(set(row.source_paths)) != len(row.source_paths):
        failures.append("duplicate source document returned")
    return tuple(failures)


def _expected_sources(case: BenchmarkCase, turn: int) -> tuple[str, ...]:
    if case.case_id in _NO_ANSWER_CASES or case.category in _NO_ANSWER_CATEGORIES:
        return ()
    if case.case_id == "G-013" and turn == 1:
        return case.source_ids[:1]
    return case.source_ids


def _source_match(
    actual_paths: tuple[str, ...],
    expected_ids: tuple[str, ...],
    case_id: str,
    turn: int,
) -> bool:
    if not expected_ids:
        return not actual_paths
    normalized_paths = tuple(path.casefold().replace("-", "") for path in actual_paths)
    matches = tuple(
        any(expected.casefold().replace("-", "") in path for path in normalized_paths)
        for expected in expected_ids
    )
    if case_id == "G-013" and turn == 2:
        return all(matches) and len(actual_paths) == len(expected_ids)
    return all(matches)


if __name__ == "__main__":
    try:
        typer.run(main)
    except (EvaluationError, HumanReviewError) as error:
        typer.echo(str(error), err=True)
        raise typer.Exit(code=2) from error
