"""Typed paired-latency statistics for benchmark result records."""

from __future__ import annotations

import math
from dataclasses import dataclass
from pathlib import Path
from typing import Final, Literal, assert_never

import numpy as np
from numpy.typing import NDArray
from pydantic import BaseModel, ConfigDict, ValidationError

FloatArray = NDArray[np.float64]
MetricName = Literal["ttft_ms", "total_ms"]
_DEFAULT_BOOTSTRAP_ITERATIONS: Final[int] = 10_000
_DEFAULT_PERMUTATION_ITERATIONS: Final[int] = 10_000
_RANDOM_SEED: Final[int] = 20260901


class ReportError(Exception):
    """Raised when benchmark results cannot be analyzed."""

    __slots__ = ("line", "path", "reason")

    path: Path
    line: int | None
    reason: str

    def __init__(self, path: Path, reason: str, line: int | None = None) -> None:
        super().__init__(reason)
        self.path = path
        self.line = line
        self.reason = reason

    def __str__(self) -> str:
        location = f"{self.path}:{self.line}" if self.line is not None else str(self.path)
        return f"benchmark report error at {location}: {self.reason}"


class ResultRow(BaseModel):
    """Validated subset of one benchmark JSONL record."""

    model_config = ConfigDict(extra="ignore", frozen=True)

    case_id: str
    repeat: int
    turn: int
    strategy: str
    ttft_ms: float | None
    total_ms: float | None
    dry_run: bool = False
    error: str | None = None


@dataclass(frozen=True, slots=True)
class LatencyPair:
    """Two successful observations sharing the same case, repeat, and turn."""

    case_id: str
    repeat: int
    turn: int
    a_ttft_ms: float
    b_ttft_ms: float
    a_total_ms: float
    b_total_ms: float


@dataclass(frozen=True, slots=True)
class MetricSummary:
    """Robust paired summary for one latency metric."""

    a_mean: float
    b_mean: float
    a_median: float
    b_median: float
    a_p95: float
    b_p95: float
    median_delta: float
    median_delta_ci_low: float
    median_delta_ci_high: float
    p95_delta: float
    p95_delta_ci_low: float
    p95_delta_ci_high: float
    relative_median_pct: float
    relative_median_ci_low: float
    relative_median_ci_high: float
    permutation_p_value: float


@dataclass(frozen=True, slots=True)
class AnalysisResult:
    """Analysis result plus the number of independent question clusters."""

    pair_count: int
    unique_case_count: int
    ttft: MetricSummary | None
    total: MetricSummary | None


def load_results(path: Path) -> tuple[ResultRow, ...]:
    """Parse benchmark JSONL rows at the external-file boundary."""
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except (FileNotFoundError, OSError, UnicodeDecodeError) as error:
        raise ReportError(path, str(error)) from error
    rows: list[ResultRow] = []
    for line_number, line in enumerate(lines, start=1):
        if not line.strip():
            continue
        try:
            rows.append(ResultRow.model_validate_json(line))
        except ValidationError as error:
            raise ReportError(path, "invalid result record", line_number) from error
    if not rows:
        raise ReportError(path, "no result records found")
    return tuple(rows)


def pair_rows(rows: tuple[ResultRow, ...], strategy_a: str, strategy_b: str) -> tuple[LatencyPair, ...]:
    """Keep only complete successful A/B pairs aligned by case and turn."""
    grouped: dict[tuple[str, int, int], dict[str, ResultRow]] = {}
    for row in rows:
        if row.dry_run or row.error is not None or not _valid_latency(row.ttft_ms) or not _valid_latency(row.total_ms):
            continue
        if row.strategy not in {strategy_a, strategy_b}:
            continue
        key = (row.case_id, row.repeat, row.turn)
        grouped.setdefault(key, {})[row.strategy] = row
    pairs: list[LatencyPair] = []
    for (case_id, repeat, turn), observations in sorted(grouped.items()):
        a = observations.get(strategy_a)
        b = observations.get(strategy_b)
        if a is None or b is None:
            continue
        assert a.ttft_ms is not None and a.total_ms is not None
        assert b.ttft_ms is not None and b.total_ms is not None
        pairs.append(LatencyPair(case_id, repeat, turn, a.ttft_ms, b.ttft_ms, a.total_ms, b.total_ms))
    return tuple(pairs)


def analyze_pairs(
    pairs: tuple[LatencyPair, ...],
    *,
    bootstrap_iterations: int = _DEFAULT_BOOTSTRAP_ITERATIONS,
    permutation_iterations: int = _DEFAULT_PERMUTATION_ITERATIONS,
) -> AnalysisResult:
    """Calculate paired bootstrap intervals and a sign-flip permutation p-value."""
    if not pairs:
        return AnalysisResult(0, 0, None, None)
    if bootstrap_iterations < 1 or permutation_iterations < 1:
        raise ReportError(Path("<arguments>"), "resampling iterations must be positive")
    rng = np.random.default_rng(_RANDOM_SEED)
    return AnalysisResult(
        len(pairs),
        len({pair.case_id for pair in pairs}),
        _summarize(pairs, "ttft_ms", bootstrap_iterations, permutation_iterations, rng),
        _summarize(pairs, "total_ms", bootstrap_iterations, permutation_iterations, rng),
    )


def _valid_latency(value: float | None) -> bool:
    return value is not None and math.isfinite(value) and value > 0


def _summarize(
    pairs: tuple[LatencyPair, ...],
    metric: MetricName,
    bootstrap_iterations: int,
    permutation_iterations: int,
    rng: np.random.Generator,
) -> MetricSummary:
    a, b = _metric_arrays(pairs, metric)
    median_delta, median_low, median_high = _bootstrap_delta(a, b, 50.0, bootstrap_iterations, rng)
    p95_delta, p95_low, p95_high = _bootstrap_delta(a, b, 95.0, bootstrap_iterations, rng)
    relative, relative_low, relative_high = _bootstrap_relative_change(a, b, bootstrap_iterations, rng)
    log_ratios = np.log(b / a)
    return MetricSummary(
        float(np.mean(a)),
        float(np.mean(b)),
        float(np.median(a)),
        float(np.median(b)),
        float(np.percentile(a, 95)),
        float(np.percentile(b, 95)),
        median_delta,
        median_low,
        median_high,
        p95_delta,
        p95_low,
        p95_high,
        relative,
        relative_low,
        relative_high,
        _permutation_p_value(log_ratios, permutation_iterations, rng),
    )


def _metric_arrays(pairs: tuple[LatencyPair, ...], metric: MetricName) -> tuple[FloatArray, FloatArray]:
    match metric:
        case "ttft_ms":
            return np.asarray([pair.a_ttft_ms for pair in pairs], dtype=np.float64), np.asarray(
                [pair.b_ttft_ms for pair in pairs], dtype=np.float64
            )
        case "total_ms":
            return np.asarray([pair.a_total_ms for pair in pairs], dtype=np.float64), np.asarray(
                [pair.b_total_ms for pair in pairs], dtype=np.float64
            )
        case unreachable:
            assert_never(unreachable)


def _bootstrap_delta(
    a: FloatArray,
    b: FloatArray,
    percentile: float,
    iterations: int,
    rng: np.random.Generator,
) -> tuple[float, float, float]:
    indices = rng.integers(0, len(a), size=(iterations, len(a)))
    deltas = np.percentile(b[indices], percentile, axis=1) - np.percentile(a[indices], percentile, axis=1)
    estimate = float(np.percentile(b, percentile) - np.percentile(a, percentile))
    return _estimate_and_interval(estimate, deltas)


def _bootstrap_relative_change(
    a: FloatArray,
    b: FloatArray,
    iterations: int,
    rng: np.random.Generator,
) -> tuple[float, float, float]:
    indices = rng.integers(0, len(a), size=(iterations, len(a)))
    changes = 100.0 * np.expm1(np.median(np.log(b[indices] / a[indices]), axis=1))
    estimate = 100.0 * math.expm1(float(np.median(np.log(b / a))))
    return _estimate_and_interval(estimate, changes)


def _estimate_and_interval(estimate: float, samples: FloatArray) -> tuple[float, float, float]:
    return estimate, float(np.percentile(samples, 2.5)), float(np.percentile(samples, 97.5))


def _permutation_p_value(log_ratios: FloatArray, iterations: int, rng: np.random.Generator) -> float:
    observed = abs(float(np.mean(log_ratios)))
    signs = rng.choice(np.asarray((-1.0, 1.0), dtype=np.float64), size=(iterations, len(log_ratios)))
    null = np.abs(np.mean(signs * log_ratios, axis=1))
    return float((np.count_nonzero(null >= observed) + 1) / (iterations + 1))
