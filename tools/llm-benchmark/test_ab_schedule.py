# /// script
# requires-python = ">=3.14"
# dependencies = ["pytest"]
# ///

# ─── How to run ───
# 1. Install uv (if not installed):
#      curl -LsSf https://astral.sh/uv/install.sh | sh
# 2. Run directly (no venv, no pip install needed):
#      uv run --with pytest pytest tools/llm-benchmark/test_ab_schedule.py
# ──────────────────

from __future__ import annotations

from ab_schedule import build_schedule


def test_build_schedule_pairs_each_case_and_balances_first_strategy() -> None:
    schedule = build_schedule(("G-001", "G-002", "G-003", "G-004"), repeats=20, seed=7)

    assert len(schedule) == 80
    assert {(trial.case_id, trial.repeat) for trial in schedule} == {
        (case_id, repeat)
        for case_id in ("G-001", "G-002", "G-003", "G-004")
        for repeat in range(1, 21)
    }
    assert abs(sum(trial.first_strategy == "raw" for trial in schedule) - 40) <= 4


def test_build_schedule_is_reproducible_for_same_seed() -> None:
    arguments = (("G-001", "G-002"), 5, 11)

    first = build_schedule(arguments[0], repeats=arguments[1], seed=arguments[2])
    second = build_schedule(arguments[0], repeats=arguments[1], seed=arguments[2])

    assert first == second
