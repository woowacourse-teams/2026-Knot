"""Typed parser for the Markdown benchmark gold set."""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

from benchmark_workload import WorkloadError, load_workload


class GoldSetError(Exception):
    """Raised when a benchmark case cannot be parsed."""

    __slots__ = ("case_id", "path", "reason")

    path: Path
    case_id: str
    reason: str

    def __init__(self, path: Path, case_id: str, reason: str) -> None:
        super().__init__(reason)
        self.path = path
        self.case_id = case_id
        self.reason = reason

    def __str__(self) -> str:
        return f"gold-set error at {self.path} ({self.case_id}): {self.reason}"


@dataclass(frozen=True, slots=True)
class BenchmarkCase:
    """A parsed benchmark case with one or more conversational turns."""

    case_id: str
    status: str
    category: str
    turns: tuple[str, ...]
    expected_answer: str
    source_ids: tuple[str, ...]
    source_ids_by_turn: tuple[tuple[str, ...], ...] | None = None

    def sources_for_turn(self, turn: int) -> tuple[str, ...]:
        """Return the source IDs expected for a one-based conversation turn."""
        if not 1 <= turn <= len(self.turns):
            raise ValueError(f"turn must be between 1 and {len(self.turns)}")
        if self.source_ids_by_turn is None:
            return self.source_ids
        return self.source_ids_by_turn[turn - 1]


def load_cases(path: Path) -> tuple[BenchmarkCase, ...]:
    """Parse Markdown G-series cases or the JSON independent workload."""
    if path.suffix.casefold() == ".json":
        return _load_json_cases(path)
    try:
        markdown = path.read_text(encoding="utf-8")
    except FileNotFoundError as error:
        raise GoldSetError(path, "unknown", "file does not exist") from error
    sections = [
        match.group(0)
        for match in re.finditer(
            r"(?ms)^###\s+G-\d+\b.*?(?=^###\s+G-\d+\b|\Z)", markdown
        )
    ]
    cases: list[BenchmarkCase] = []
    for section in sections:
        case_id = re.match(r"^###\s+(G-\d+)", section)
        if case_id is None:
            raise GoldSetError(path, "unknown", "case heading is malformed")
        identifier = case_id.group(1)
        turns = _turns(section)
        if not turns:
            raise GoldSetError(
                path, identifier, "question or conversation turns are missing"
            )
        cases.append(
            BenchmarkCase(
                identifier,
                _field(section, "상태"),
                _field(section, "유형"),
                turns,
                _expected_answer(section),
                _source_ids(section),
            )
        )
    if not cases:
        raise GoldSetError(path, "unknown", "no G-series cases found")
    return tuple(cases)


def _load_json_cases(path: Path) -> tuple[BenchmarkCase, ...]:
    try:
        manifest = load_workload(path)
    except WorkloadError as error:
        raise GoldSetError(path, "unknown", error.reason) from error
    return tuple(
        BenchmarkCase(
            case.case_id,
            "needs-human",
            case.category.value,
            case.turns,
            "\n".join(case.expected_facts),
            case.expected_source_ids,
            case.expected_source_ids_by_turn,
        )
        for case in manifest.cases
    )


def _field(section: str, label: str) -> str:
    match = re.search(rf"(?m)^-\s+{re.escape(label)}:\s*(.+?)\s*$", section)
    return match.group(1).replace("`", "").strip() if match else ""


def _turns(section: str) -> tuple[str, ...]:
    question = re.search(r"(?m)^-\s+질문:\s*`([^`]+)`", section)
    if question:
        return (question.group(1).strip(),)
    return tuple(
        turn.strip()
        for turn in re.findall(r"(?m)^\s*사용자:\s*(.+?)\s*$", section)
        if turn.strip()
    )


def _expected_answer(section: str) -> str:
    match = re.search(
        r"(?ms)^-\s+기대 답변:\s*\n(?P<body>.*?)(?=^\s*-\s+(?:정답 문서|관련 문서|실패 판정|기준 문서|최종 확정 조건)|\Z)",
        section,
    )
    if match is None:
        return ""
    return "\n".join(
        line.lstrip().removeprefix("> ") for line in match.group("body").splitlines()
    ).strip()


def _source_ids(section: str) -> tuple[str, ...]:
    ids: list[str] = []
    collecting_nested = False
    for line in section.splitlines():
        direct = re.match(
            r"^\s*-\s+page ID:\s*`?([A-Za-z0-9][A-Za-z0-9-]*)`?\s*$", line
        )
        if direct is not None:
            ids.append(direct.group(1))
            collecting_nested = False
            continue
        if re.match(r"^\s*-\s+page ID:\s*$", line):
            collecting_nested = True
            continue
        if collecting_nested:
            nested = re.match(r"^\s+-\s+`?([A-Za-z0-9][A-Za-z0-9-]*)`?\s*$", line)
            if nested is not None:
                ids.append(nested.group(1))
                continue
            if line.strip() and not line.startswith((" ", "\t")):
                collecting_nested = False
    return tuple(ids)
