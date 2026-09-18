"""Balanced, reproducible order scheduling for paired A/B trials."""

from __future__ import annotations

import random
from dataclasses import dataclass
from typing import Literal, assert_never

StrategyLabel = Literal["raw", "rag"]


class ScheduleError(Exception):
    """Raised when an A/B schedule cannot be constructed."""

    __slots__ = ("reason",)

    reason: str

    def __init__(self, reason: str) -> None:
        super().__init__(reason)
        self.reason = reason

    def __str__(self) -> str:
        return f"A/B schedule error: {self.reason}"


@dataclass(frozen=True, slots=True)
class Trial:
    """One paired case/repeat with a randomized first strategy."""

    case_id: str
    repeat: int
    first_strategy: StrategyLabel
    second_strategy: StrategyLabel


def build_schedule(case_ids: tuple[str, ...], repeats: int, seed: int) -> tuple[Trial, ...]:
    """Create a balanced schedule with the same case paired in both orders."""
    if not case_ids:
        raise ScheduleError("at least one case is required")
    if repeats < 1:
        raise ScheduleError("repeats must be positive")
    order_count = len(case_ids) * repeats
    orders: list[StrategyLabel] = ["raw"] * (order_count // 2)
    orders.extend(["rag"] * (order_count - len(orders)))
    random.Random(seed).shuffle(orders)
    schedule: list[Trial] = []
    order_index = 0
    for repeat in range(1, repeats + 1):
        for case_id in case_ids:
            first_strategy = orders[order_index]
            order_index += 1
            schedule.append(Trial(case_id, repeat, first_strategy, _other_strategy(first_strategy)))
    return tuple(schedule)


def _other_strategy(strategy: StrategyLabel) -> StrategyLabel:
    match strategy:
        case "raw":
            return "rag"
        case "rag":
            return "raw"
        case unreachable:
            assert_never(unreachable)
