"""Typed result structures shared by access analysis and report rendering."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class StrategySummary:
    """Latency, reliability, and source-hit summary for one strategy."""

    strategy: str
    records: int
    successful_model_records: int
    error_records: int
    search_p50: float
    search_p95: float
    search_mean: float
    embedding_p50: float
    database_p50: float
    model_ttft_p50: float | None
    model_total_p50: float | None
    e2e_ttft_p50: float | None
    e2e_total_p50: float | None
    e2e_ttft_under_5s: int
    e2e_ttft_observations: int
    source_hit_count: int
    source_quality_count: int


@dataclass(frozen=True, slots=True)
class PairSummary:
    """Paired delta summary where positive means B is slower than A."""

    metric: str
    strategy_a: str
    strategy_b: str
    pairs: int
    median_delta: float
    ci_low: float
    ci_high: float
    b_faster: int
    p_value: float
