from __future__ import annotations

import json
from itertools import count
from uuid import UUID

import httpx2
import pytest
from pydantic import SecretStr

from assistant import KnotAssistant
from failure_details import describe_failure
from notion_api import NotionReader
from notion_lookup import ScopedLookup
from notion_models import PageQuery, Record
from settings import AssistantFailure, NotionLookup, NotionSource, ROOT_PAGE_ID, Settings

ROOT = UUID(ROOT_PAGE_ID)
PAGE = UUID(int=1)
BLOCK = UUID(int=2)
DATABASE = UUID(int=3)
SOURCE = UUID(int=4)


def test_data_source_schema_is_not_parsed_as_page_values() -> None:
    source = Record.model_validate({"object": "data_source", "id": str(SOURCE), "properties": {"Name": {"type": "title", "title": {}}}})
    assert source.properties == {}


@pytest.fixture
def anyio_backend() -> str:
    return "asyncio"


@pytest.fixture(autouse=True)
def no_rate_delay(monkeypatch: pytest.MonkeyPatch) -> None:
    ticks = count(100)
    monkeypatch.setattr("notion_api.monotonic", lambda: float(next(ticks)))


@pytest.mark.anyio
async def test_outside_page_is_denied_before_body_read() -> None:
    requests: list[httpx2.Request] = []

    def respond(request: httpx2.Request) -> httpx2.Response:
        requests.append(request)
        return httpx2.Response(200, json={"object": "page", "id": str(PAGE), "parent": {"type": "workspace", "workspace": True}})

    async with httpx2.AsyncClient(base_url="https://api.notion.com/v1/", transport=httpx2.MockTransport(respond)) as http:
        with pytest.raises(AssistantFailure, match="notion_outside_knot"):
            await ScopedLookup(NotionReader(http)).read_page(PAGE)
    assert len(requests) == 1
    assert requests[0].url.path == f"/v1/pages/{PAGE}"


@pytest.mark.anyio
async def test_database_row_ancestry_and_nested_blocks() -> None:
    metadata = {
        f"pages/{ROOT}": {"object": "page", "id": str(ROOT)},
        f"databases/{DATABASE}": {"object": "database", "id": str(DATABASE), "parent": {"page_id": str(ROOT)}, "data_sources": [{"id": str(SOURCE)}]},
        f"data_sources/{SOURCE}": {"object": "data_source", "id": str(SOURCE), "parent": {"database_id": str(DATABASE)}},
        f"pages/{PAGE}": {"object": "page", "id": str(PAGE), "parent": {"data_source_id": str(SOURCE)}, "properties": {"Name": {"type": "title", "title": [{"plain_text": "커밋 컨벤션"}]}}},
    }

    def respond(request: httpx2.Request) -> httpx2.Response:
        path = request.url.path.removeprefix("/v1/")
        if path in metadata:
            return httpx2.Response(200, json=metadata[path])
        if path == f"data_sources/{SOURCE}/query":
            assert request.method == "POST"
            return httpx2.Response(200, json={"results": [metadata[f"pages/{PAGE}"]], "has_more": False})
        if path == f"blocks/{PAGE}/children":
            return httpx2.Response(200, json={"results": [{"object": "block", "id": str(BLOCK), "type": "toggle", "has_children": True, "toggle": {"rich_text": [{"plain_text": "타입"}]}}], "has_more": False})
        assert path == f"blocks/{BLOCK}/children"
        return httpx2.Response(200, json={"results": [{"object": "block", "id": str(UUID(int=5)), "type": "paragraph", "paragraph": {"rich_text": [{"plain_text": "feat, fix, docs"}]}}], "has_more": False})

    async with httpx2.AsyncClient(base_url="https://api.notion.com/v1/", transport=httpx2.MockTransport(respond)) as http:
        lookup = ScopedLookup(NotionReader(http))
        await lookup.read_database(DATABASE)
        result = lookup.result()
    assert "feat, fix, docs" in result.context
    assert result.sources[0].title == "커밋 컨벤션"
    assert result.coverage is not None and not result.coverage.complete


@pytest.mark.anyio
async def test_pagination_uses_get_cursor_and_rejects_write_paths() -> None:
    requests: list[httpx2.Request] = []

    def respond(request: httpx2.Request) -> httpx2.Response:
        requests.append(request)
        first = len(requests) == 1
        return httpx2.Response(200, json={"results": [], "has_more": first, "next_cursor": str(BLOCK) if first else None})

    async with httpx2.AsyncClient(base_url="https://api.notion.com/v1/", transport=httpx2.MockTransport(respond)) as http:
        reader = NotionReader(http)
        await reader.list_records(f"blocks/{PAGE}/children", PageQuery())
        with pytest.raises(AssistantFailure, match="notion_readonly_violation"):
            await reader.read(f"pages/{PAGE}", PageQuery())
    assert len(requests) == 2
    assert all(request.method == "GET" for request in requests)
    assert requests[1].url.params["start_cursor"] == str(BLOCK)


@pytest.mark.anyio
async def test_n8n_503_falls_back_without_user_failure_notice(monkeypatch: pytest.MonkeyPatch) -> None:
    settings = Settings(N8N_NOTION_WEBHOOK_URL="https://n8n.aitestbed.kr/webhook/test", N8N_WEBHOOK_SECRET=SecretStr("synthetic"))

    async def direct(question: str, queries: tuple[str, ...]) -> NotionLookup:
        assert question == "커밋 컨벤션"
        assert queries == ("커밋",)
        return NotionLookup(context="feat, fix", sources=[NotionSource(title="커밋", url=f"https://www.notion.so/{PAGE.hex}")])

    monkeypatch.setattr("assistant.lookup_via_mcp", direct)
    async with KnotAssistant(settings) as client:
        await client.setup_hook()
        assert client.notion_http is not None
        await client.notion_http.aclose()
        async with httpx2.AsyncClient(transport=httpx2.MockTransport(lambda _request: httpx2.Response(503))) as http:
            client.notion_http = http
            lookup = await client.retrieve_notion("커밋 컨벤션", ("커밋",))
    assert lookup.context == "feat, fix"
    assert lookup.notices == []


@pytest.mark.anyio
async def test_both_failures_identify_provider_and_cause(monkeypatch: pytest.MonkeyPatch) -> None:
    async def direct(_question: str, _queries: tuple[str, ...]) -> NotionLookup:
        raise AssistantFailure("notion_http_401")

    monkeypatch.setattr("assistant.lookup_via_mcp", direct)
    settings = Settings(N8N_NOTION_WEBHOOK_URL="https://n8n.aitestbed.kr/webhook/test", N8N_WEBHOOK_SECRET=SecretStr("synthetic"))
    async with KnotAssistant(settings) as client:
        await client.setup_hook()
        assert client.notion_http is not None
        await client.notion_http.aclose()
        async with httpx2.AsyncClient(transport=httpx2.MockTransport(lambda _request: httpx2.Response(503))) as http:
            client.notion_http = http
            with pytest.raises(AssistantFailure) as failure:
                await client.retrieve_notion("커밋")
    diagnosis = describe_failure(failure.value, "Notion 문서 조회")
    assert "n8n" in diagnosis.message and "HTTP 503" in diagnosis.message
    assert "MCP" in diagnosis.message and "액세스 키가 유효하지 않아" in diagnosis.message


@pytest.mark.anyio
@pytest.mark.parametrize("mode", ["timeout", "empty", "unrelated", "malformed"])
async def test_unusable_primary_results_recover_silently(monkeypatch: pytest.MonkeyPatch, mode: str) -> None:
    recovered = NotionLookup(context="초대 정책", sources=[NotionSource(title="초대", url=f"https://www.notion.so/{PAGE.hex}")])

    async def primary(_client: KnotAssistant, _question: str) -> NotionLookup:
        if mode == "timeout":
            raise TimeoutError
        if mode == "malformed":
            return NotionLookup.model_validate({"context": 10})
        if mode == "unrelated":
            return NotionLookup(context="커밋 규칙", sources=[NotionSource(title="커밋", url=f"https://www.notion.so/{BLOCK.hex}")])
        return NotionLookup()

    async def direct(_question: str, _queries: tuple[str, ...]) -> NotionLookup:
        return recovered

    monkeypatch.setattr(KnotAssistant, "retrieve_n8n", primary)
    monkeypatch.setattr("assistant.lookup_via_mcp", direct)
    settings = Settings(N8N_NOTION_WEBHOOK_URL="https://n8n.aitestbed.kr/webhook/test", N8N_WEBHOOK_SECRET=SecretStr("synthetic"))
    async with KnotAssistant(settings) as client:
        await client.setup_hook()
        result = await client.retrieve_notion("초대", ("초대",))
    assert result == recovered
    assert not result.notices


@pytest.mark.anyio
async def test_prepared_search_reads_topic_and_rules_and_late_evidence() -> None:
    policy = UUID(int=6)
    seen_queries: list[str] = []
    pages = {
        PAGE: {"object": "page", "id": str(PAGE), "parent": {"page_id": str(ROOT)}, "properties": {"Name": {"title": [{"plain_text": "초대 공유"}]}}},
        policy: {"object": "page", "id": str(policy), "parent": {"page_id": str(ROOT)}, "properties": {"Name": {"title": [{"plain_text": "도메인 규칙"}]}}},
    }

    def respond(request: httpx2.Request) -> httpx2.Response:
        if request.url.path == "/v1/search":
            query = json.loads(request.content)["query"]
            seen_queries.append(query)
            return httpx2.Response(200, json={"results": [page for page in pages.values() if query in page["properties"]["Name"]["title"][0]["plain_text"]], "has_more": False})
        if request.url.path == f"/v1/pages/{ROOT}":
            return httpx2.Response(200, json={"object": "page", "id": str(ROOT)})
        if "/pages/" in request.url.path:
            return httpx2.Response(200, json=pages[UUID(request.url.path.rsplit("/", 1)[1])])
        text = "무관한 문맥\n" * 700 + "초대 계약: 새 초대를 생성해도 기존 초대를 무효화하지 않음"
        return httpx2.Response(200, json={"results": [{"object": "block", "id": str(BLOCK), "type": "paragraph", "paragraph": {"rich_text": [{"plain_text": text}]}}], "has_more": False})

    async with httpx2.AsyncClient(base_url="https://api.notion.com/v1/", transport=httpx2.MockTransport(respond)) as http:
        lookup = ScopedLookup(NotionReader(http))
        await lookup.search("현재 프로젝트 단일 모드/복수 모드?", ("초대", "도메인 규칙"))
        result = lookup.result()
    assert seen_queries == ["초대", "도메인 규칙"]
    assert {source.title for source in result.sources} == {"초대 공유", "도메인 규칙"}
    assert "기존 초대를 무효화하지 않음" in result.context


@pytest.mark.anyio
@pytest.mark.parametrize("fuzzy_results", [False, True])
async def test_no_title_match_retries_topic_word_and_reads_commit_document(fuzzy_results: bool) -> None:
    seen: list[str] = []
    page = {"object": "page", "id": str(PAGE), "parent": {"page_id": str(ROOT)}, "properties": {"Name": {"title": [{"plain_text": "커밋 컨벤션"}]}}}
    unrelated = {"object": "page", "id": str(UUID(int=6)), "parent": {"page_id": str(ROOT)}, "properties": {"Name": {"title": [{"plain_text": "유형 참고 문서"}]}}}

    def respond(request: httpx2.Request) -> httpx2.Response:
        if request.url.path == "/v1/search":
            term = json.loads(request.content)["query"]
            seen.append(term)
            results = [page] if term == "커밋" else ([unrelated] if fuzzy_results else [])
            return httpx2.Response(200, json={"results": results, "has_more": False})
        if "/pages/" in request.url.path:
            metadata = {PAGE: page, UUID(int=6): unrelated, ROOT: {"object": "page", "id": str(ROOT)}}
            return httpx2.Response(200, json=metadata[UUID(request.url.path.rsplit("/", 1)[1])])
        return httpx2.Response(200, json={"results": [{"object": "block", "id": str(BLOCK), "type": "paragraph", "paragraph": {"rich_text": [{"plain_text": "feat fix docs refactor chore"}]}}], "has_more": False})

    async with httpx2.AsyncClient(base_url="https://api.notion.com/v1/", transport=httpx2.MockTransport(respond)) as http:
        lookup = ScopedLookup(NotionReader(http))
        await lookup.search("프론트엔드 커밋 타입", ("프론트엔드", "커밋 타입", "개발 컨벤션"))
        result = lookup.result()
    assert "커밋 타입" in seen and "커밋" in seen
    assert "타입" not in seen
    assert result.sources[0].title == "커밋 컨벤션"
    assert "feat fix docs refactor chore" in result.context
