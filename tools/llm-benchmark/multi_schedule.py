"""Balanced randomized schedules for multi-strategy benchmark trials."""

from __future__ import annotations

import random
from dataclasses import dataclass

from access_context import AccessLabel


class ScheduleError(Exception):
    """Raised when a multi-strategy schedule cannot be constructed."""

    __slots__ = ("reason",)

    reason: str

    def __init__(self, reason: str) -> None:
        super().__init__(reason)
        self.reason = reason

    def __str__(self) -> str:
        return f"multi-strategy schedule error: {self.reason}"


@dataclass(frozen=True, slots=True)
class MultiTrial:
    """One case/repeat with every selected strategy in a randomized order."""

    case_id: str
    repeat: int
    order: tuple[AccessLabel, ...]


def build_schedule(
    case_ids: tuple[str, ...],
    strategies: tuple[AccessLabel, ...],
    repeats: int,
    seed: int,
) -> tuple[MultiTrial, ...]:
    """Create a reproducible schedule that runs each strategy once per pair."""
    if not case_ids:
        raise ScheduleError("at least one case is required")
    if not strategies:
        raise ScheduleError("at least one strategy is required")
    if len(set(strategies)) != len(strategies):
        raise ScheduleError("strategies must be unique")
    if repeats < 1:
        raise ScheduleError("repeats must be positive")
    rng = random.Random(seed)
    schedule: list[MultiTrial] = []
    for repeat in range(1, repeats + 1):
        for case_id in case_ids:
            order = list(strategies)
            rng.shuffle(order)
            schedule.append(MultiTrial(case_id, repeat, tuple(order)))
    return tuple(schedule)
