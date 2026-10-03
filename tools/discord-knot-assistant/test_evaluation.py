from pathlib import Path

import pytest
from pydantic import SecretStr

from assistant import KnotAssistant
from evaluation import EvaluationReport, review_answer, run_case, save_report
from evaluation_cases import CASES, EvaluationCase, Review, check_answer
from codex_cli import visible_answer
from question_classifier import QuestionClassification
from question_routing import QuestionRoute, ReasoningEffort
from settings import AssistantFailure, NotionLookup, NotionSource, Settings


@pytest.fixture
def anyio_backend() -> str:
    return "asyncio"


def test_guard_rejects_unsupported_links_and_recovered_failure() -> None:
    lookup = NotionLookup(sources=[NotionSource(title="초대", url="https://www.notion.so/allowed")])
    issues = check_answer("복수야. n8n HTTP 503 실패. [근거](https://www.notion.so/invented)", lookup, CASES[0], True)
    assert "unsupported_citation" in issues
    assert "recovered_error_leaked" in issues


def test_guard_requires_evidence_and_correct_chat_route() -> None:
    assert "missing_citation" in check_answer("복수 모드야", NotionLookup(), CASES[0], True)
    assert "retrieval_route_mismatch" in check_answer("하이 ㅋㅋ", NotionLookup(), CASES[4], True)
    assert check_answer("하이 ㅋㅋ 질문 기다리는 중이야", NotionLookup(), CASES[4], False) == []


def test_no_sources_cannot_preserve_invented_document_link() -> None:
    assert visible_answer("[초대 문서](https://www.notion.so/invented)", NotionLookup()) == "초대 문서"


def test_numbered_sources_are_checked_then_rendered_from_actual_metadata() -> None:
    lookup = NotionLookup(sources=[NotionSource(title="초대", url="https://www.notion.so/allowed")])
    assert check_answer("복수 모드야. [출처 1]", lookup, CASES[0], True) == []
    assert visible_answer("복수 모드야. [출처 1]", lookup) == "복수 모드야. [초대](https://www.notion.so/allowed)"
    assert "unsupported_citation" in check_answer("복수야. [출처 2]", lookup, CASES[0], True)
    assert visible_answer("[출처 2]", lookup) == ""


@pytest.mark.anyio
@pytest.mark.parametrize("judge_fails", [True, False])
async def test_evaluation_cannot_pass_with_broken_judge_or_leaked_failure(monkeypatch: pytest.MonkeyPatch, judge_fails: bool) -> None:
    route = QuestionRoute(effort=ReasoningEffort.LOW, retrieve_notion=False)
    monkeypatch.setattr("evaluation.classify_question", lambda _question: QuestionClassification(route, "luna_low"))
    monkeypatch.setattr("evaluation.codex_answer", lambda _prompt, _request: "하이. n8n HTTP 503 실패")

    def review(_case: EvaluationCase, _answer: str, _reference: str, _lookup: NotionLookup, _model: str) -> Review:
        if judge_fails:
            raise AssistantFailure("codex_timeout")
        return Review(passed=True, issues=[])

    monkeypatch.setattr("evaluation.review_answer", review)
    settings = Settings(N8N_NOTION_WEBHOOK_URL="https://n8n.aitestbed.kr/webhook/test", N8N_WEBHOOK_SECRET=SecretStr("synthetic"))
    async with KnotAssistant(settings) as client:
        result = await run_case(client, CASES[4], 1, "")
    assert not result.passed
    assert "recovered_error_leaked" in result.issues
    if judge_fails:
        assert "pipeline_failed:codex_timeout" in result.issues


def test_report_is_private_and_does_not_pass_before_completion(tmp_path: Path) -> None:
    report = EvaluationReport(started_at="test", model="test", required_rounds=2)
    path = tmp_path / "report.json"
    save_report(report, path)
    assert path.stat().st_mode & 0o777 == 0o600
    assert not EvaluationReport.model_validate_json(path.read_text()).passed


def test_judge_receives_independent_reference_and_all_retrieved_evidence(monkeypatch: pytest.MonkeyPatch) -> None:
    lookup = NotionLookup(context="배포 문서의 추가 근거", sources=[NotionSource(title="배포 문서", url="https://www.notion.so/deployment")])
    prompts: list[str] = []

    def judge(prompt: str, _request: object) -> str:
        prompts.append(prompt)
        return '{"passed":true,"issues":[]}'

    monkeypatch.setattr("evaluation.codex_answer", judge)
    assert review_answer(CASES[3], "배포 시각은 확인할 수 없어", "초대 정책 기준", lookup, "test").passed
    assert "초대 정책 기준" in prompts[0]
    assert "배포 문서의 추가 근거" in prompts[0]
    assert "https://www.notion.so/deployment" in prompts[0]
    assert "잘못 인용" in prompts[0]
