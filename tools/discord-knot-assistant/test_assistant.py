from __future__ import annotations

import json
import subprocess
from pathlib import Path

import discord.http
import httpx2
import pytest
from pydantic import SecretStr

from assistant import KnotAssistant
from codex_cli import CodexRequest, codex_answer, make_prompt, response_chunks, visible_answer
from question_routing import QuestionRoute, ReasoningEffort, route_question
from settings import GUILD_ID, NotionCoverage, NotionLookup, NotionSource, Settings
from sync_guard import claim_sync, finish_sync


@pytest.fixture
def anyio_backend() -> str:
    return "asyncio"


@pytest.mark.anyio
async def test_discord_client_and_pending_notion_request(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    settings = Settings(
        N8N_NOTION_WEBHOOK_URL="https://n8n.aitestbed.kr/webhook/test",
        N8N_WEBHOOK_SECRET=SecretStr("synthetic-test-secret"),
    )
    requests: list[httpx2.Request] = []
    state_path = tmp_path / "sync.json"
    monkeypatch.setattr("assistant.claim_sync", lambda: claim_sync(state_path))
    monkeypatch.setattr("assistant.finish_sync", lambda: finish_sync(state_path))

    def respond(request: httpx2.Request) -> httpx2.Response:
        requests.append(request)
        if len(requests) <= 2:
            return httpx2.Response(202, json={"pending": True})
        return httpx2.Response(200, json={"context": "조회된 문서", "sources": []})

    async with KnotAssistant(settings) as client:
        assert isinstance(client.http, discord.http.HTTPClient)
        await client.setup_hook()
        assert client.notion_http is not None
        await client.notion_http.aclose()
        async with httpx2.AsyncClient(transport=httpx2.MockTransport(respond)) as notion_http:
            client.notion_http = notion_http
            lookup = await client.retrieve_n8n("문서 질문")
        assert lookup.context == "조회된 문서"
        assert isinstance(client.http, discord.http.HTTPClient)

    first = json.loads(requests[0].content)
    second = json.loads(requests[1].content)
    third = json.loads(requests[2].content)
    assert first["guild_id"] == str(GUILD_ID)
    assert first["action"] == "status"
    assert second["action"] == "ask"
    assert third["action"] == "status"
    assert requests[0].headers["X-Knot-Assistant-Secret"] == "synthetic-test-secret"


def test_recovered_failure_is_not_in_answer_prompt() -> None:
    prompt = make_prompt("문서 질문", NotionLookup(warnings=["일부 문서 접근 불가"], notices=["HTTP 503"]), 12000, "초대 정책 확인")
    assert "일부 문서 접근 불가" not in prompt
    assert "HTTP 503" not in prompt
    assert "초대 정책 확인" in prompt
    assert "전체 문서를 확인했다고 말하지 마" in prompt


def test_reply_chunks_preserve_long_text() -> None:
    answer = "가" * 4100
    chunks = response_chunks(answer)
    assert all(len(chunk) <= 1800 for chunk in chunks)
    assert "".join(chunks) == answer


def test_successful_reply_does_not_append_operational_status() -> None:
    lookup = NotionLookup(
        coverage=NotionCoverage(complete=False, documents=436, requests=0, cached=True),
        warnings=["일부 문서 접근 불가"],
    )
    assert visible_answer("문서에 있는 답변", lookup) == "문서에 있는 답변"
    assert visible_answer("가벼운 대화", NotionLookup()) == "가벼운 대화"
    assert visible_answer("답변", NotionLookup(refreshing=True, notices=["HTTP 503"])) == "답변"


def test_sync_claim_is_shared_and_released(tmp_path: Path) -> None:
    state_path = tmp_path / "sync.json"
    assert claim_sync(state_path)
    assert not claim_sync(state_path)
    finish_sync(state_path)
    assert claim_sync(state_path)


def test_reply_corrects_document_link_from_verified_source() -> None:
    lookup = NotionLookup(sources=[NotionSource(title="그라운드 룰", url="https://www.notion.so/known")])
    result = visible_answer("[그라운드 룰](https://www.notion.so/invented)", lookup)
    assert result == "[그라운드 룰](https://www.notion.so/known)"
    missing = visible_answer("[다른 문서](https://www.notion.so/invented)", lookup)
    assert "invented" not in missing
    assert "https://www.notion.so/known" not in missing


@pytest.mark.parametrize(
    ("question", "effort", "retrieve_notion"),
    [
        ("", ReasoningEffort.LOW, False),
        ("안녕하세요!", ReasoningEffort.LOW, False),
        ("오늘 뭐해 ㅋㅋ", ReasoningEffort.LOW, False),
        ("고마워ㅎㅎ", ReasoningEffort.LOW, False),
        ("HELLO!", ReasoningEffort.LOW, False),
        ("우리 이번주 스프린트 목표가 뭐지?", ReasoningEffort.MEDIUM, True),
        ("안녕! Knot 그라운드 룰 알려줘", ReasoningEffort.MEDIUM, True),
        ("오늘 뭐해? 노션 일정도 알려줘", ReasoningEffort.MEDIUM, True),
        ("처음 보는 일반 질문", ReasoningEffort.MEDIUM, True),
        ("고마워, 인증 설계의 장단점 비교해줘", ReasoningEffort.MAX, True),
        ("응답 지연 원인을 분석해줘", ReasoningEffort.MAX, True),
        ("이 버그 디버깅해줘", ReasoningEffort.MAX, True),
        ("Compare the architecture tradeoffs", ReasoningEffort.MAX, True),
        ("긴 질문 " * 130, ReasoningEffort.MAX, True),
    ],
)
def test_question_selects_effort_and_document_retrieval(
    question: str, effort: ReasoningEffort, retrieve_notion: bool
) -> None:
    route = route_question(question)
    assert route == QuestionRoute(effort=effort, retrieve_notion=retrieve_notion)


@pytest.mark.parametrize("effort", list(ReasoningEffort))
def test_codex_receives_selected_reasoning_effort(
    monkeypatch: pytest.MonkeyPatch, effort: ReasoningEffort
) -> None:
    commands: list[list[str]] = []
    monkeypatch.setattr("codex_cli.shutil.which", lambda _name: "/synthetic/codex")

    def complete(command: list[str], **_kwargs: str | bool | int) -> subprocess.CompletedProcess[str]:
        commands.append(command)
        event = {"type": "item.completed", "item": {"type": "agent_message", "text": "완료"}}
        return subprocess.CompletedProcess(command, 0, stdout=json.dumps(event), stderr="")

    monkeypatch.setattr("codex_cli.subprocess.run", complete)
    answer = codex_answer("synthetic prompt", CodexRequest(model="gpt-5.6-luna", effort=effort))
    assert answer == "완료"
    assert f'model_reasoning_effort="{effort.value}"' in commands[0]
    assert "--sandbox" in commands[0]
