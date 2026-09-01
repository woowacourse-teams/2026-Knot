# /// script
# requires-python = ">=3.14"
# dependencies = ["numpy", "pydantic", "pytest", "typer"]
# ///

# ─── How to run ───
# 1. Install uv (if not installed):
#      curl -LsSf https://astral.sh/uv/install.sh | sh
# 2. Run directly (no venv, no pip install needed):
#      uv run --with numpy --with pydantic --with pytest pytest tools/llm-benchmark/test_analyze_benchmark.py
# ──────────────────

from __future__ import annotations

from analysis_stats import ResultRow, analyze_pairs, pair_rows


def test_pair_rows_aligns_same_case_turn_across_strategies() -> None:
    rows = (
        ResultRow(case_id="G-001", repeat=1, turn=1, strategy="raw", ttft_ms=100.0, total_ms=200.0, dry_run=False),
        ResultRow(case_id="G-001", repeat=1, turn=1, strategy="rag", ttft_ms=50.0, total_ms=120.0, dry_run=False),
    )

    pairs = pair_rows(rows, "raw", "rag")

    assert len(pairs) == 1
    assert pairs[0].a_ttft_ms == 100.0
    assert pairs[0].b_ttft_ms == 50.0


def test_analyze_pairs_reports_faster_b_with_confidence_shape() -> None:
    rows = tuple(
        ResultRow(
            case_id=f"G-{index:03d}",
            repeat=1,
            turn=1,
            strategy=strategy,
            ttft_ms=latency,
            total_ms=latency * 2,
            dry_run=False,
        )
        for index, latency in enumerate((100.0, 110.0, 90.0, 105.0), start=1)
        for strategy, latency in (("raw", latency), ("rag", latency / 2))
    )

    result = analyze_pairs(pair_rows(rows, "raw", "rag"), bootstrap_iterations=200, permutation_iterations=500)

    assert result.pair_count == 4
    assert result.unique_case_count == 4
    assert result.ttft.b_median < result.ttft.a_median
    assert 0.0 <= result.ttft.permutation_p_value <= 1.0
    assert result.ttft.median_delta_ci_low <= result.ttft.median_delta_ci_high
