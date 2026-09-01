#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.14"
# dependencies = [
#     "httpx2[http2,brotli,zstd]",
#     "pydantic",
#     "pydantic-settings",
#     "rich",
#     "typer",
# ]
# ///

# ruff: noqa: B008  # Typer uses Option calls as the CLI declaration syntax.

# ─── How to run ───
# 1. Install uv (if not installed):
#      curl -LsSf https://astral.sh/uv/install.sh | sh
# 2. Run directly (no venv, no pip install needed):
#      uv run tools/llm-benchmark/run_benchmark.py --help
# 3. Or make executable and run:
#      chmod +x tools/llm-benchmark/run_benchmark.py && ./tools/llm-benchmark/run_benchmark.py --help
# ──────────────────

from __future__ import annotations

import json
import time
from contextlib import closing
from dataclasses import asdict, dataclass
from enum import StrEnum
from pathlib import Path
from typing import Final, TextIO, assert_never

import typer
from benchmark_core import (
    ContextPack,
    Document,
    SnapshotError,
    Strategy,
    build_context,
    load_snapshot,
)
from gold_set import BenchmarkCase, GoldSetError, load_cases
from nim_client import (
    ChatMessage,
    NimClient,
    NimConfigurationError,
    NimRequestError,
    NimSettings,
    NimTransportError,
)
from pydantic import ValidationError
from rich.console import Console

_DEFAULT_SNAPSHOT = Path(".benchmark-data/notion-export")
_DEFAULT_GOLD_SET = Path("docs/llm-search-benchmark-gold-set.md")
_DEFAULT_OUTPUT = Path(".benchmark-data/results.jsonl")
_SYSTEM_INSTRUCTIONS: Final[str] = """당신은 Knot 팀 문서 검색 평가용 답변자입니다.
반드시 제공된 문서 컨텍스트만 근거로 답하세요.
근거가 없으면 찾지 못했다고 말하고, 문서가 충돌하면 각 주장을 나열한 뒤 최종 결정을 단정하지 마세요.
답변 끝에는 사용한 source_path를 [source: 경로] 형식으로 표시하세요.
"""


class RunMode(StrEnum):
    """CLI strategy selection."""

    ALL = "all"
    RAW = "raw"
    RAG = "rag"
    MCP_REPLAY = "mcp-replay"


@dataclass(frozen=True, slots=True)
class BenchmarkRecord:
    """One JSONL-compatible observation from a benchmark turn."""

    case_id: str
    repeat: int
    turn: int
    strategy: str
    question: str
    answer: str
    source_paths: tuple[str, ...]
    retrieved_count: int
    tool_calls: int
    context_chars: int
    search_ms: float
    model_ttft_ms: float | None
    model_total_ms: float | None
    ttft_ms: float | None
    total_ms: float | None
    dry_run: bool
    error: str | None
    embedding_ms: float = 0.0
    vector_db_ms: float = 0.0


def main(
    snapshot_dir: Path = typer.Option(_DEFAULT_SNAPSHOT, help="Notion Markdown/CSV export directory."),
    gold_set: Path = typer.Option(_DEFAULT_GOLD_SET, help="Benchmark gold-set Markdown file."),
    output: Path = typer.Option(_DEFAULT_OUTPUT, help="JSONL result output path."),
    strategy: RunMode = typer.Option(RunMode.ALL, help="Strategy to run."),
    case: str | None = typer.Option(None, help="Comma-separated case IDs, for example G-001,G-007."),
    repeats: int = typer.Option(1, min=1, help="Number of independent runs per case and strategy."),
    top_k: int = typer.Option(5, min=1, help="Number of lexical chunks for RAG and MCP replay."),
    dry_run: bool = typer.Option(False, help="Load and plan requests without calling NIM."),
    list_cases: bool = typer.Option(False, help="Print parsed case IDs and exit."),
) -> None:
    """Run comparable Raw, RAG, and MCP replay NIM benchmark requests."""
    console = Console()
    cases = load_cases(gold_set)
    if list_cases:
        _print_cases(console, cases)
        return
    documents = load_snapshot(snapshot_dir)
    selected_cases = _select_cases(cases, case)
    selected_strategies = _strategies(strategy)
    output.parent.mkdir(parents=True, exist_ok=True)
    settings = None if dry_run else _load_settings(console)
    with output.open("w", encoding="utf-8") as stream:
        if settings is None:
            _write_dry_run(console, stream, selected_cases, documents, selected_strategies, top_k)
            return
        with closing(NimClient(settings)) as client:
            for repeat_number in range(1, repeats + 1):
                for selected_strategy in selected_strategies:
                    for benchmark_case in selected_cases:
                        _run_case(
                            stream,
                            client,
                            repeat_number,
                            benchmark_case,
                            documents,
                            selected_strategy,
                            top_k,
                        )
    console.print(f"[green]results written:[/green] {output}")


def _run_case(
    stream: TextIO,
    client: NimClient,
    repeat_number: int,
    benchmark_case: BenchmarkCase,
    documents: tuple[Document, ...],
    strategy: Strategy,
    top_k: int,
) -> None:
    history: list[ChatMessage] = []
    for turn_number, question in enumerate(benchmark_case.turns, start=1):
        search_started = time.perf_counter()
        context = build_context(strategy, documents, question, top_k)
        search_ms = (time.perf_counter() - search_started) * 1000
        messages = _messages(question, context, tuple(history))
        try:
            result = client.generate(messages)
            record = BenchmarkRecord(
                benchmark_case.case_id,
                repeat_number,
                turn_number,
                strategy.value,
                question,
                result.text,
                context.source_paths,
                context.retrieved_count,
                context.tool_calls,
                len(context.text),
                search_ms,
                result.ttft_ms,
                result.total_ms,
                search_ms + result.ttft_ms,
                search_ms + result.total_ms,
                False,
                None,
            )
            history.extend(
                (
                    ChatMessage(role="user", content=question),
                    ChatMessage(role="assistant", content=result.text),
                )
            )
        except (NimRequestError, NimTransportError) as error:
            record = BenchmarkRecord(
                benchmark_case.case_id,
                repeat_number,
                turn_number,
                strategy.value,
                question,
                "",
                context.source_paths,
                context.retrieved_count,
                context.tool_calls,
                len(context.text),
                search_ms,
                None,
                None,
                None,
                None,
                False,
                str(error),
            )
        _write_record(stream, record)


def _messages(
    question: str,
    context: ContextPack,
    history: tuple[ChatMessage, ...],
) -> tuple[ChatMessage, ...]:
    prompt = f"""<documents>\n{context.text or '(검색된 문서 없음)'}\n</documents>\n\n질문: {question}"""
    return (ChatMessage(role="system", content=_SYSTEM_INSTRUCTIONS), *history, ChatMessage(role="user", content=prompt))


def _write_dry_run(
    console: Console,
    stream: TextIO,
    cases: tuple[BenchmarkCase, ...],
    documents: tuple[Document, ...],
    strategies: tuple[Strategy, ...],
    top_k: int,
) -> None:
    for selected_strategy in strategies:
        for benchmark_case in cases:
            for turn_number, question in enumerate(benchmark_case.turns, start=1):
                search_started = time.perf_counter()
                context = build_context(selected_strategy, documents, question, top_k)
                search_ms = (time.perf_counter() - search_started) * 1000
                _write_record(
                    stream,
                    BenchmarkRecord(
                        benchmark_case.case_id,
                        0,
                        turn_number,
                        selected_strategy.value,
                        question,
                        "",
                        context.source_paths,
                        context.retrieved_count,
                        context.tool_calls,
                        len(context.text),
                        search_ms,
                        None,
                        None,
                        None,
                        None,
                        True,
                        None,
                    ),
                )
    console.print(f"[yellow]dry-run complete:[/yellow] {len(cases)} cases, {len(strategies)} strategies")


def _write_record(stream: TextIO, record: BenchmarkRecord) -> None:
    stream.write(json.dumps(asdict(record), ensure_ascii=False, sort_keys=True) + "\n")


def _load_settings(console: Console) -> NimSettings:
    try:
        return NimSettings()
    except ValidationError as error:
        console.print("[red]invalid NIM environment:[/red]", error)
        raise typer.Exit(code=2) from error


def _select_cases(cases: tuple[BenchmarkCase, ...], selection: str | None) -> tuple[BenchmarkCase, ...]:
    if selection is None:
        return cases
    wanted = frozenset(item.strip() for item in selection.split(",") if item.strip())
    selected = tuple(benchmark_case for benchmark_case in cases if benchmark_case.case_id in wanted)
    if len(selected) != len(wanted):
        missing = ", ".join(sorted(wanted - {item.case_id for item in selected}))
        raise typer.BadParameter(f"unknown case ID(s): {missing}")
    return selected


def _strategies(mode: RunMode) -> tuple[Strategy, ...]:
    match mode:
        case RunMode.ALL:
            return (Strategy.RAW, Strategy.RAG, Strategy.MCP_REPLAY)
        case RunMode.RAW:
            return (Strategy.RAW,)
        case RunMode.RAG:
            return (Strategy.RAG,)
        case RunMode.MCP_REPLAY:
            return (Strategy.MCP_REPLAY,)
        case unreachable:
            assert_never(unreachable)


def _print_cases(console: Console, cases: tuple[BenchmarkCase, ...]) -> None:
    for benchmark_case in cases:
        console.print(f"{benchmark_case.case_id}\t{benchmark_case.category}\t{len(benchmark_case.turns)} turn(s)")


if __name__ == "__main__":
    try:
        typer.run(main)
    except (GoldSetError, SnapshotError, NimConfigurationError) as error:
        Console().print(f"[red]{error}[/red]")
        raise typer.Exit(code=2) from error
