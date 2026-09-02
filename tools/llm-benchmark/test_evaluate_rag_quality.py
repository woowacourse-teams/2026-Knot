"""Tests for the RAG quality evaluation gates."""

from __future__ import annotations

from answer_quality_policy import answer_shape_passes
from evaluate_rag_quality import EvaluationRow, evaluate, evaluate_human_labels
from gold_set import BenchmarkCase
from human_review import HumanReviewRow, HumanReviewStatus


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


def test_answer_gate_fails_when_any_evaluated_turn_has_no_policy() -> None:
    cases = (_case("G-007", ("첫 질문", "후속 질문"), ("policy",)),)
    rows = (
        EvaluationRow(
            case_id="G-007",
            repeat=1,
            turn=1,
            strategy="rag",
            answer="정책에 없는 답변",
            source_paths=("policy",),
            retrieved_count=1,
        ),
        EvaluationRow(
            case_id="G-007",
            repeat=1,
            turn=2,
            strategy="rag",
            answer="로드맵의 level 3 기능은 흑곰 기획안 요구사항을 기준으로 정리했습니다.",
            source_paths=("policy",),
            retrieved_count=1,
        ),
    )

    summary = evaluate(rows, cases, "rag", repeats=1)

    assert summary.retrieval_gate_passed
    assert summary.answer_evaluated == 2
    assert summary.answer_passed == 1
    assert summary.answer_gate_passed is False
    assert summary.answer_failures[0].startswith("('G-007', 1, 1):")


def test_human_gate_requires_one_terminal_label_per_expected_turn() -> None:
    # Given: two generated turns and a complete human review for only one of them
    cases = (_case("G-007", ("첫 질문", "후속 질문"), ("policy",)),)
    reviewed = HumanReviewRow(
        case_id="G-007",
        repeat=1,
        turn=1,
        strategy="rag",
        decision="pass",
        answer_correct=True,
        sources_relevant=True,
        policy_compliant=True,
        reviewer="reviewer-a",
    )

    # When: the evaluator checks human semantic coverage
    summary = evaluate_human_labels((reviewed,), cases, "rag", repeat=1)

    # Then: the unreviewed turn keeps the gate pending
    assert summary.status is HumanReviewStatus.PENDING
    assert not summary.gate_passed
    assert summary.missing == (("G-007", 1, 2, "rag"),)


def test_unregistered_workload_cases_are_left_for_human_answer_review() -> None:
    # Given: a new independent workload case without a brittle lexical policy
    # When: the automatic answer-shape checker sees it
    result = answer_shape_passes("문서 근거를 확인했습니다.", "W-001", 1)

    # Then: semantic quality remains explicitly unevaluated by automation
    assert result is None
