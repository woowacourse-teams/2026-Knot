"""Tests for the RAG quality evaluation gates."""

from __future__ import annotations

from answer_quality_policy import answer_shape_passes
from evaluate_rag_quality import EvaluationRow, evaluate, evaluate_human_labels
from gold_set import BenchmarkCase
from human_review import HumanReviewRow, HumanReviewStatus


def _case(
    case_id: str,
    turns: tuple[str, ...],
    sources: tuple[str, ...],
    category: str = "test",
) -> BenchmarkCase:
    return BenchmarkCase(case_id, "confirmed", category, turns, "", sources)


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
            source_paths=(),
            retrieved_count=0,
        ),
        EvaluationRow(
            case_id="G-012",
            repeat=1,
            turn=1,
            strategy="rag",
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
            source_paths=(sources[0],),
            retrieved_count=1,
        ),
        EvaluationRow(
            case_id="G-013",
            repeat=1,
            turn=2,
            strategy="rag",
            source_paths=sources,
            retrieved_count=3,
        ),
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


def test_answer_gate_ignores_turns_without_automatic_policy() -> None:
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
    assert summary.answer_evaluated == 1
    assert summary.answer_passed == 1
    assert summary.answer_gate_passed is None
    assert summary.answer_failures == ()


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
            source_paths=(),
            retrieved_count=0,
        ),
        EvaluationRow(
            case_id="W-031",
            repeat=1,
            turn=1,
            strategy="rag",
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
    )

    # When: the selected result repeat is passed to the human gate
    summary = evaluate_human_labels((reviewed,), (case,), "rag", repeat=2)

    # Then: the label covers the requested repeat exactly
    assert summary.gate_passed
    assert summary.expected == 1
