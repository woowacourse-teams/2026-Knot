"""Tests for the RAG quality evaluation gates."""

from __future__ import annotations

from evaluate_rag_quality import EvaluationRow, evaluate
from gold_set import BenchmarkCase


def _case(case_id: str, turns: tuple[str, ...], sources: tuple[str, ...]) -> BenchmarkCase:
    return BenchmarkCase(case_id, "confirmed", "test", turns, "", sources)


def test_no_answer_and_clarification_require_empty_sources() -> None:
    cases = (
        _case("G-011", ("MongoDB?",), ("856de115-6a83-8253-96be-810bf12c4384",)),
        _case("G-012", ("넓은 질문",), ("856de115-6a83-8253-96be-810bf12c4384",)),
    )
    rows = (
        EvaluationRow(case_id="G-011", repeat=1, turn=1, strategy="rag", source_paths=(), retrieved_count=0),
        EvaluationRow(case_id="G-012", repeat=1, turn=1, strategy="rag", source_paths=(), retrieved_count=0),
    )

    summary = evaluate(rows, cases, "rag", repeats=1)

    assert summary.retrieval_gate_passed
    assert summary.answer_gate_passed is None


def test_related_documents_must_match_all_three_sources() -> None:
    sources = (
        "fffde1156a83837097bc818fab8a1fa4.md",
        "5eade1156a8383878f7981b89626dfe4.md",
        "3abde1156a8382f0a64501fa2f046d15.md",
    )
    cases = (_case("G-013", ("질문", "추가"), sources),)
    rows = (
        EvaluationRow(case_id="G-013", repeat=1, turn=1, strategy="rag", source_paths=(sources[0],), retrieved_count=1),
        EvaluationRow(case_id="G-013", repeat=1, turn=2, strategy="rag", source_paths=sources, retrieved_count=3),
    )

    summary = evaluate(rows, cases, "rag", repeats=1)

    assert summary.retrieval_gate_passed


def test_answer_gate_checks_policy_shape_when_answers_exist() -> None:
    cases = (_case("G-012", ("넓은 질문",), ("policy",)),)
    rows = (
        EvaluationRow(
            case_id="G-012",
            repeat=1,
            turn=1,
            strategy="rag",
            answer="범위가 넓어요. 최근 결정사항, 로드맵, 백엔드 진행 상황 중 어떤 내용을 찾고 싶나요?",
            source_paths=(),
        ),
    )

    summary = evaluate(rows, cases, "rag", repeats=1)

    assert summary.retrieval_gate_passed
    assert summary.answer_gate_passed is True
