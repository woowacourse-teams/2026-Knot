from __future__ import annotations

import json
import subprocess
from pathlib import Path

import pytest

from codex_cli import CodexRequest, codex_answer
from question_classifier import classify_question
from question_routing import QuestionRoute, ReasoningEffort
from settings import AssistantFailure


def test_model_route_takes_precedence_over_local_fallback(monkeypatch: pytest.MonkeyPatch) -> None:
    requests: list[CodexRequest] = []

    def classify(_prompt: str, request: CodexRequest) -> str:
        requests.append(request)
        assert request.output_schema is not None
        schema = json.loads(request.output_schema.read_text(encoding="utf-8"))
        assert set(schema["required"]) == {"effort", "retrieve_notion", "intent", "search_queries"}
        return '{"effort":"medium","retrieve_notion":true,"intent":"초대 발급 정책 확인","search_queries":["초대","도메인 규칙"]}'

    monkeypatch.setattr("question_classifier.codex_answer", classify)

    result = classify_question("안녕")

    assert result.route == QuestionRoute(effort=ReasoningEffort.MEDIUM, retrieve_notion=True)
    assert result.source == "luna_low"
    assert result.intent == "초대 발급 정책 확인"
    assert result.search_queries == ("초대", "도메인 규칙")
    assert requests[0].model == "gpt-5.6-luna"
    assert requests[0].effort == ReasoningEffort.LOW
    assert requests[0].timeout_seconds == 25


@pytest.mark.parametrize(
    "output",
    [
        "not json",
        '{"effort":"ultra","retrieve_notion":false}',
        '{"effort":"low","retrieve_notion":"false"}',
        '{"effort":"low","retrieve_notion":false,"command":"run"}',
        '{"effort":"medium","retrieve_notion":true,"intent":"정책","search_queries":["a","b","c","d","e"]}',
    ],
)
def test_invalid_model_output_uses_local_fallback(monkeypatch: pytest.MonkeyPatch, output: str) -> None:
    monkeypatch.setattr("question_classifier.codex_answer", lambda _prompt, _request: output)

    result = classify_question("Knot의 스프린트 목표 알려줘")

    assert result.route == QuestionRoute(effort=ReasoningEffort.MEDIUM, retrieve_notion=True)
    assert result.source == "local_fallback"
    assert result.failure_code == "ValidationError"


def test_classifier_timeout_preserves_complex_route(monkeypatch: pytest.MonkeyPatch) -> None:
    def timeout(_prompt: str, _request: CodexRequest) -> str:
        raise AssistantFailure("codex_timeout")

    monkeypatch.setattr("question_classifier.codex_answer", timeout)

    result = classify_question("Knot의 인증 설계 장단점 비교해줘")

    assert result.route == QuestionRoute(effort=ReasoningEffort.MAX, retrieve_notion=True)
    assert result.source == "local_fallback"
    assert result.failure_code == "codex_timeout"


def test_codex_forwards_schema_and_timeout(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    commands: list[list[str]] = []
    timeouts: list[str | bool | int] = []
    schema = tmp_path / "route.json"
    monkeypatch.setattr("codex_cli.shutil.which", lambda _name: "/synthetic/codex")

    def complete(command: list[str], **kwargs: str | bool | int) -> subprocess.CompletedProcess[str]:
        commands.append(command)
        timeouts.append(kwargs["timeout"])
        event = {"type": "item.completed", "item": {"type": "agent_message", "text": "{}"}}
        return subprocess.CompletedProcess(command, 0, stdout=json.dumps(event), stderr="")

    monkeypatch.setattr("codex_cli.subprocess.run", complete)

    codex_answer("synthetic", CodexRequest("gpt-5.6-luna", ReasoningEffort.LOW, schema, 9))

    assert commands[0][-3:] == ["--output-schema", str(schema), "-"]
    assert timeouts == [9]
