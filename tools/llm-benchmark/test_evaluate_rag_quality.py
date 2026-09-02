"""Tests for the RAG quality evaluation gates."""

from __future__ import annotations

import pytest
from answer_quality_policy import answer_shape_passes
from benchmark_result_identity import observation_fingerprint
from evaluate_rag_quality import EvaluationRow, evaluate, evaluate_human_labels
from gold_set import BenchmarkCase
from human_review import HumanReviewRow, HumanReviewStatus
from pydantic import ValidationError


def _case(
    case_id: str,
    turns: tuple[str, ...],
    sources: tuple[str, ...],
    category: str = "test",
    sources_by_turn: tuple[tuple[str, ...], ...] | None = None,
) -> BenchmarkCase:
    return BenchmarkCase(
        case_id,
        "confirmed",
        category,
        turns,
        "",
        sources,
        sources_by_turn,
    )


def _fingerprint(result: EvaluationRow) -> str:
    return observation_fingerprint(
        case_id=result.case_id,
        repeat=result.repeat,
        turn=result.turn,
        strategy=result.strategy,
        question=result.question,
        answer=result.answer,
        source_paths=result.source_paths,
        retrieved_count=result.retrieved_count,
        error=result.error,
    )


def test_no_answer_and_clarification_require_empty_sources() -> None:
    cases = (
        _case("G-011", ("MongoDB?",), ("856de115-6a83-8253-96be-810bf12c4384",)),
        _case("G-012", ("넓은 질문",), ("856de115-6a83-8253-96be-810bf12c4384",)),
    )
    rows = (
        EvaluationRow(
            case_id="G-011",
            repeat=1,
            turn=1,
            strategy="rag",
            question="MongoDB?",
            answer="",
            source_paths=(),
            retrieved_count=0,
        ),
        EvaluationRow(
            case_id="G-012",
            repeat=1,
            turn=1,
            strategy="rag",
            question="넓은 질문",
            answer="",
            source_paths=(),
            retrieved_count=0,
        ),
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
        EvaluationRow(
            case_id="G-013",
            repeat=1,
            turn=1,
            strategy="rag",
            question="질문",
            answer="",
            source_paths=(sources[0],),
            retrieved_count=1,
        ),
        EvaluationRow(
            case_id="G-013",
            repeat=1,
            turn=2,
            strategy="rag",
            question="추가",
            answer="",
            source_paths=sources,
            retrieved_count=3,
        ),
    )

    summary = evaluate(rows, cases, "rag", repeats=1)

    assert summary.retrieval_gate_passed


def test_source_matching_rejects_substrings_and_extra_documents() -> None:
    # Given: an expected page ID and a near-match plus an unrelated page
    cases = (_case("G-013", ("질문",), ("page-1", "page-2")),)
    rows = (
        EvaluationRow(
            case_id="G-013",
            repeat=1,
            turn=1,
            strategy="rag",
            question="질문",
            answer="",
            source_paths=("page-10", "unrelated.md"),
            retrieved_count=2,
        ),
    )

    # Then: substring and extra-document matches cannot satisfy the source gate
    summary = evaluate(rows, cases, "rag", repeats=1)

    assert not summary.retrieval_gate_passed


def test_evaluation_rejects_a_question_that_differs_from_the_workload_turn() -> None:
    # Given: a result identity whose question is not the declared workload question
    cases = (_case("W-001", ("원래 질문",), ("page-1",)),)
    rows = (
        EvaluationRow(
            case_id="W-001",
            repeat=1,
            turn=1,
            strategy="rag",
            question="다른 질문",
            answer="답변",
            source_paths=("page-1",),
            retrieved_count=1,
        ),
    )

    # When: the result is evaluated against the workload manifest
    summary = evaluate(rows, cases, "rag", repeats=1)

    # Then: a row cannot pass by reusing another question's identity
    assert not summary.retrieval_gate_passed
    assert any("question" in failure for failure in summary.retrieval_failures)


def test_follow_up_source_expectations_are_checked_per_turn() -> None:
    # Given: each conversational turn has a different expected evidence page
    cases = (
        _case(
            "W-001",
            ("첫 질문", "후속 질문"),
            ("page-1", "page-2"),
            sources_by_turn=(("page-1",), ("page-2",)),
        ),
    )
    rows = (
        EvaluationRow(
            case_id="W-001",
            repeat=1,
            turn=1,
            strategy="rag",
            question="첫 질문",
            answer="",
            source_paths=("page-1",),
            retrieved_count=1,
        ),
        EvaluationRow(
            case_id="W-001",
            repeat=1,
            turn=2,
            strategy="rag",
            question="후속 질문",
            answer="",
            source_paths=("page-2",),
            retrieved_count=1,
        ),
    )

    # Then: a turn must not inherit the union of all case sources
    summary = evaluate(rows, cases, "rag", repeats=1)

    assert summary.retrieval_gate_passed


def test_evaluation_row_requires_complete_observation_fields() -> None:
    # Given: an identity-only JSON record
    # When & then: it cannot enter the quality gate with inferred defaults
    with pytest.raises(ValidationError):
        EvaluationRow(
            case_id="W-001",
            repeat=1,
            turn=1,
            strategy="rag",
        )


def test_human_terminal_label_is_bound_to_the_observed_result() -> None:
    # Given: one exact generated observation and its terminal human label
    case = _case("W-001", ("질문",), ("page-1",))
    result = EvaluationRow(
        case_id="W-001",
        repeat=1,
        turn=1,
        strategy="rag",
        question="질문",
        answer="확인된 답변",
        source_paths=("page-1",),
        retrieved_count=1,
    )
    fingerprint = observation_fingerprint(
        case_id=result.case_id,
        repeat=result.repeat,
        turn=result.turn,
        strategy=result.strategy,
        question=result.question,
        answer=result.answer,
        source_paths=result.source_paths,
        retrieved_count=result.retrieved_count,
        error=result.error,
    )
    label = HumanReviewRow(
        case_id="W-001",
        repeat=1,
        turn=1,
        strategy="rag",
        decision="pass",
        answer_correct=True,
        sources_relevant=True,
        policy_compliant=True,
        reviewer="reviewer-a",
        result_fingerprint=fingerprint,
    )

    # When: the evaluator binds the label to the actual result row
    summary = evaluate_human_labels(
        (label,), (case,), "rag", repeat=1, result_rows=(result,)
    )

    # Then: a matching immutable observation is required for a human pass
    assert summary.gate_passed


def test_human_terminal_label_fails_when_the_observed_answer_changes() -> None:
    # Given: a label created for an earlier answer and a changed result row
    case = _case("W-001", ("질문",), ("page-1",))
    original = EvaluationRow(
        case_id="W-001",
        repeat=1,
        turn=1,
        strategy="rag",
        question="질문",
        answer="원래 답변",
        source_paths=("page-1",),
        retrieved_count=1,
    )
    changed = original.model_copy(update={"answer": "변경된 답변"})
    label = HumanReviewRow(
        case_id="W-001",
        repeat=1,
        turn=1,
        strategy="rag",
        decision="pass",
        answer_correct=True,
        sources_relevant=True,
        policy_compliant=True,
        reviewer="reviewer-a",
        result_fingerprint=observation_fingerprint(
            case_id=original.case_id,
            repeat=original.repeat,
            turn=original.turn,
            strategy=original.strategy,
            question=original.question,
            answer=original.answer,
            source_paths=original.source_paths,
            retrieved_count=original.retrieved_count,
            error=original.error,
        ),
    )

    # When: the label is checked against the changed result
    summary = evaluate_human_labels(
        (label,), (case,), "rag", repeat=1, result_rows=(changed,)
    )

    # Then: a label cannot be reused after the observed answer changes
    assert not summary.gate_passed
    assert summary.invalid == (("W-001", 1, 1, "rag"),)


def test_human_gate_rejects_a_pass_for_an_empty_or_failed_answer() -> None:
    # Given: a retrieval-only result without a successful answer
    case = _case("W-001", ("질문",), ("page-1",))
    result = EvaluationRow(
        case_id="W-001",
        repeat=1,
        turn=1,
        strategy="rag",
        question="질문",
        answer="",
        source_paths=(),
        retrieved_count=0,
    )
    label = HumanReviewRow(
        case_id="W-001",
        repeat=1,
        turn=1,
        strategy="rag",
        decision="pass",
        answer_correct=True,
        sources_relevant=True,
        policy_compliant=True,
        reviewer="reviewer-a",
        result_fingerprint=_fingerprint(result),
    )

    # When: a terminal pass is evaluated against that observation
    summary = evaluate_human_labels(
        (label,), (case,), "rag", repeat=1, result_rows=(result,)
    )

    # Then: human review cannot turn retrieval-only output into a quality pass
    assert not summary.gate_passed
    assert summary.invalid == (("W-001", 1, 1, "rag"),)


def test_evaluation_row_requires_the_question_identity() -> None:
    # Given: a result record without the question that produced it
    with pytest.raises(ValidationError):
        EvaluationRow(
            case_id="W-001",
            repeat=1,
            turn=1,
            strategy="rag",
            answer="답변",
            source_paths=(),
            retrieved_count=0,
        )


def test_answer_gate_checks_policy_shape_when_answers_exist() -> None:
    cases = (_case("G-012", ("넓은 질문",), ("policy",)),)
    rows = (
        EvaluationRow(
            case_id="G-012",
            repeat=1,
            turn=1,
            strategy="rag",
            question="넓은 질문",
            answer="범위가 넓어요. 최근 결정사항, 로드맵, 백엔드 진행 상황 중 어떤 내용을 찾고 싶나요?",
            source_paths=(),
            retrieved_count=0,
        ),
    )

    summary = evaluate(rows, cases, "rag", repeats=1)

    assert summary.retrieval_gate_passed
    assert summary.answer_gate_passed is True


def test_answer_gate_ignores_turns_without_automatic_policy() -> None:
    cases = (_case("G-007", ("첫 질문", "후속 질문"), ("policy",)),)
    rows = (
        EvaluationRow(
            case_id="G-007",
            repeat=1,
            turn=1,
            strategy="rag",
            question="첫 질문",
            answer="정책에 없는 답변",
            source_paths=("policy",),
            retrieved_count=1,
        ),
        EvaluationRow(
            case_id="G-007",
            repeat=1,
            turn=2,
            strategy="rag",
            question="후속 질문",
            answer="로드맵의 level 3 기능은 흑곰 기획안 요구사항을 기준으로 정리했습니다.",
            source_paths=("policy",),
            retrieved_count=1,
        ),
    )

    summary = evaluate(rows, cases, "rag", repeats=1)

    assert summary.retrieval_gate_passed
    assert summary.answer_evaluated == 1
    assert summary.answer_passed == 1
    assert summary.answer_gate_passed is None
    assert summary.answer_failures == ()


def test_human_gate_requires_one_terminal_label_per_expected_turn() -> None:
    # Given: two generated turns and a complete human review for only one of them
    cases = (_case("G-007", ("첫 질문", "후속 질문"), ("policy",)),)
    result = EvaluationRow(
        case_id="G-007",
        repeat=1,
        turn=1,
        strategy="rag",
        question="첫 질문",
        answer="답변",
        source_paths=("policy",),
        retrieved_count=1,
    )
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
        result_fingerprint=observation_fingerprint(
            case_id=result.case_id,
            repeat=result.repeat,
            turn=result.turn,
            strategy=result.strategy,
            question=result.question,
            answer=result.answer,
            source_paths=result.source_paths,
            retrieved_count=result.retrieved_count,
            error=result.error,
        ),
    )

    # When: the evaluator checks human semantic coverage
    summary = evaluate_human_labels(
        (reviewed,), cases, "rag", repeat=1, result_rows=(result,)
    )

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


def test_no_answer_and_broad_workload_cases_must_not_expose_sources() -> None:
    # Given: independent cases whose source IDs are reviewer evidence, not answer links
    cases = (
        _case("W-030", ("MongoDB?",), ("policy",), "no_answer"),
        _case("W-031", ("전체 현황?",), ("policy",), "broad"),
    )
    rows = (
        EvaluationRow(
            case_id="W-030",
            repeat=1,
            turn=1,
            strategy="rag",
            question="MongoDB?",
            answer="",
            source_paths=(),
            retrieved_count=0,
        ),
        EvaluationRow(
            case_id="W-031",
            repeat=1,
            turn=1,
            strategy="rag",
            question="전체 현황?",
            answer="",
            source_paths=(),
            retrieved_count=0,
        ),
    )

    # When: the automatic retrieval gate evaluates those policy cases
    summary = evaluate(rows, cases, "rag", repeats=1)

    # Then: no-answer and clarification responses pass without related documents
    assert summary.retrieval_gate_passed


def test_human_gate_can_target_a_specific_result_repeat() -> None:
    # Given: a terminal review label for repeat two
    case = _case("W-001", ("질문",), ("policy",))
    result = EvaluationRow(
        case_id="W-001",
        repeat=2,
        turn=1,
        strategy="rag",
        question="질문",
        answer="답변",
        source_paths=("policy",),
        retrieved_count=1,
    )
    reviewed = HumanReviewRow(
        case_id="W-001",
        repeat=2,
        turn=1,
        strategy="rag",
        decision="pass",
        answer_correct=True,
        sources_relevant=True,
        policy_compliant=True,
        reviewer="reviewer-a",
        result_fingerprint=observation_fingerprint(
            case_id=result.case_id,
            repeat=result.repeat,
            turn=result.turn,
            strategy=result.strategy,
            question=result.question,
            answer=result.answer,
            source_paths=result.source_paths,
            retrieved_count=result.retrieved_count,
            error=result.error,
        ),
    )

    # When: the selected result repeat is passed to the human gate
    summary = evaluate_human_labels(
        (reviewed,), (case,), "rag", repeat=2, result_rows=(result,)
    )

    # Then: the label covers the requested repeat exactly
    assert summary.gate_passed
    assert summary.expected == 1
