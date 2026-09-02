#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.14"
# dependencies = ["httpx2[http2,brotli,zstd]", "pydantic", "pydantic-settings", "pytest"]
# ///

# ─── How to run ───
# 1. Install uv (if not installed):
#      curl -LsSf https://astral.sh/uv/install.sh | sh
# 2. Run directly:
#      uv run --with httpx2 --with pydantic --with pydantic-settings --with pytest pytest tools/llm-benchmark/test_mcp_adapter.py
# ──────────────────

from __future__ import annotations

from pathlib import Path

import pytest
from benchmark_core import Document
from mcp_adapter import (
    McpAdapterError,
    McpScopeError,
    ReplayMcpAdapter,
    build_mcp_context,
    execute_validated_tool_call,
)
from mcp_models import (
    JsonObject,
    McpFetchResult,
    McpPage,
    McpScope,
    McpSearchHit,
    McpSearchResult,
    McpToolResult,
    McpToolTrace,
    NimFunctionCall,
    NimToolCall,
    validate_nim_tool_call,
)
from mcp_transport import McpToolExchange


class _FakeMcpClient:
    """Deterministic MCP caller for adapter contract tests."""

    def __init__(
        self, results: list[McpToolExchange], calls: list[tuple[str, JsonObject]]
    ) -> None:
        self.results = results
        self.calls = calls

    def call_tool(self, name: str, arguments: JsonObject) -> McpToolExchange:
        self.calls.append((name, arguments))
        return self.results.pop(0)


def _scope() -> McpScope:
    return McpScope("workspace-a", "snapshot-1", frozenset({"page-1", "page-2"}))


def _page(
    page_id: str,
    content: str,
    *,
    workspace_id: str = "workspace-a",
    snapshot_id: str | None = "snapshot-1",
) -> McpPage:
    return McpPage(
        page_id,
        page_id.title(),
        f"https://notion.so/{page_id}",
        content,
        workspace_id,
        snapshot_id,
    )


def test_replay_adapter_returns_only_active_scope_and_preserves_page_metadata() -> None:
    # Given: a replay corpus containing an allowed page and two out-of-scope pages
    adapter = ReplayMcpAdapter(
        (
            _page("page-1", "PostgreSQL 결정"),
            _page("page-3", "PostgreSQL other"),
            _page("page-2", "Redis"),
        ),
        _scope(),
    )

    # When: the benchmark searches and fetches the matching result
    search = adapter.search("PostgreSQL", limit=3)
    fetched = adapter.fetch(search.hits[0])

    # Then: only the active page is exposed with provenance and timing data
    assert tuple(hit.page_id for hit in search.hits) == ("page-1",)
    assert fetched.page.content == "PostgreSQL 결정"
    assert fetched.page.workspace_id == "workspace-a"
    assert fetched.page.snapshot_id == "snapshot-1"
    assert search.trace.http_requests == 0
    assert fetched.trace.page_count == 1


def test_replay_adapter_from_documents_keeps_the_same_page_identity_as_the_snapshot() -> (
    None
):
    # Given: a normalized document path containing its Notion page ID
    document = Document(
        Path("회의록/로드맵 기반 기획 회의 453de1156a838282959681990c718da2.md"),
        "로드맵 회의",
        "로드맵 결정",
    )
    adapter = ReplayMcpAdapter.from_documents(
        (document,),
        "workspace-a",
        "snapshot-1",
        frozenset({"453de1156a838282959681990c718da2"}),
    )

    # When & then: replay exposes the snapshot document using the same source identity
    result = adapter.search("로드맵", limit=1)
    assert result.hits[0].page_id == "453de1156a838282959681990c718da2"
    assert adapter.fetch(result.hits[0]).page.title == "로드맵 회의"


def test_validated_nim_tool_call_is_the_only_path_to_execute_search_or_fetch() -> None:
    # Given: a read-only replay adapter and a model-produced search call
    page = _page("page-1", "PostgreSQL 결정")
    adapter = ReplayMcpAdapter((page,), _scope())
    call = validate_nim_tool_call(
        NimToolCall(
            id="search-1",
            function=NimFunctionCall(
                name="notion-search", arguments='{"query":"PostgreSQL"}'
            ),
        )
    )

    # When: the validated call is executed at the tool boundary
    result = execute_validated_tool_call(call, adapter, _scope(), search_limit=1)

    # Then: the model gets a scoped read result and no arbitrary operation is accepted
    assert result.hits[0].page_id == "page-1"


def test_replay_adapter_rejects_a_fetch_for_a_page_outside_scope() -> None:
    # Given: a caller tries to fetch an ID that was not returned from the scoped search
    adapter = ReplayMcpAdapter((_page("page-1", "allowed"),), _scope())
    outside = McpSearchHit(
        "page-3", "Outside", "https://notion.so/page-3", "", "workspace-a", "snapshot-1"
    )

    # When & then: the adapter denies the cross-range read
    with pytest.raises(McpScopeError):
        adapter.fetch(outside)


def test_build_mcp_context_fetches_at_most_top_k_and_exposes_tool_traces() -> None:
    # Given: two pages in the replay adapter
    adapter = ReplayMcpAdapter(
        (_page("page-1", "PostgreSQL"), _page("page-2", "PostgreSQL pgvector")),
        _scope(),
    )

    # When: a context is built with one requested source
    timing = build_mcp_context(adapter, "PostgreSQL", top_k=1)

    # Then: the answer context contains one source and search + fetch tool accounting
    assert timing.context.retrieved_count == 1
    assert timing.context.tool_calls == 2
    assert timing.context.source_paths == ("https://notion.so/page-1",)
    assert tuple(trace.operation for trace in timing.traces) == ("search", "fetch")


def test_live_adapter_paginates_search_and_normalizes_fetch_content() -> None:
    # Given: a live-shaped MCP response split across two search pages and one detail response
    first_search = McpToolExchange(
        McpToolResult.model_validate(
            {
                "structuredContent": {
                    "results": [
                        {
                            "id": "page-1",
                            "url": "https://notion.so/page-1",
                            "title": "DB",
                            "snippet": "PostgreSQL",
                            "workspace_id": "workspace-a",
                            "snapshot_id": "snapshot-1",
                        }
                    ],
                    "has_more": True,
                    "next_cursor": "cursor-2",
                }
            }
        ),
        1,
        0,
        0,
        2.0,
    )
    second_search = McpToolExchange(
        McpToolResult.model_validate(
            {
                "structuredContent": {
                    "results": [
                        {
                            "id": "page-2",
                            "url": "https://notion.so/page-2",
                            "title": "Vector",
                            "snippet": "pgvector",
                            "workspace_id": "workspace-a",
                            "snapshot_id": "snapshot-1",
                        }
                    ],
                    "has_more": False,
                }
            }
        ),
        1,
        0,
        0,
        3.0,
    )
    detail = McpToolExchange(
        McpToolResult.model_validate(
            {
                "content": [{"type": "text", "text": "# DB\nPostgreSQL과 pgvector"}],
                "structuredContent": {
                    "id": "page-1",
                    "title": "DB",
                    "last_edited_time": "2026-08-19T00:00:00Z",
                },
            }
        ),
        1,
        0,
        0,
        4.0,
    )
    client = _FakeMcpClient([first_search, second_search, detail], [])
    from mcp_adapter import LiveNotionMcpAdapter

    adapter = LiveNotionMcpAdapter(
        client, _scope(), search_tool="notion-search", fetch_tool="notion-fetch"
    )

    # When: the adapter searches and fetches the first result
    search = adapter.search("PostgreSQL", limit=2)
    fetched = adapter.fetch(search.hits[0])

    # Then: pagination is accounted for and only normalized page content reaches context
    assert tuple(hit.page_id for hit in search.hits) == ("page-1", "page-2")
    assert fetched.page.title == "DB"
    assert fetched.page.content == "# DB\nPostgreSQL과 pgvector"
    assert search.trace.page_count == 2
    assert search.trace.http_requests == 2
    assert fetched.trace.http_requests == 1
    assert client.calls == [
        ("notion-search", {"query": "PostgreSQL"}),
        ("notion-search", {"query": "PostgreSQL", "cursor": "cursor-2"}),
        ("notion-fetch", {"url": "https://notion.so/page-1"}),
    ]


@pytest.mark.parametrize(
    "metadata",
    (
        {"workspace_id": "workspace-a"},
        {"snapshot_id": "snapshot-1"},
        {},
    ),
)
def test_live_fetch_requires_explicit_scope_metadata(
    metadata: dict[str, str],
) -> None:
    # Given: a detail response whose scope identity is incomplete
    result = McpToolExchange(
        McpToolResult.model_validate(
            {
                "structuredContent": {
                    "id": "page-1",
                    "title": "DB",
                    **metadata,
                },
                "content": [{"type": "text", "text": "PostgreSQL"}],
            }
        ),
        1,
        0,
        0,
        1.0,
    )
    client = _FakeMcpClient([result], [])
    from mcp_adapter import LiveNotionMcpAdapter

    adapter = LiveNotionMcpAdapter(client, _scope())
    hit = McpSearchHit(
        "page-1", "DB", "https://notion.so/page-1", "", "workspace-a", "snapshot-1"
    )

    # When & then: the detail cannot inherit missing scope identity from the search hit
    with pytest.raises(McpScopeError, match="scope metadata"):
        adapter.fetch(hit)


def test_live_fetch_rejects_markdown_only_detail_without_scope_proof() -> None:
    # Given: a Markdown link that identifies the page but carries no trusted scope metadata
    result = McpToolExchange(
        McpToolResult.model_validate(
            {
                "content": [
                    {
                        "type": "text",
                        "text": "# DB\n[DB](https://notion.so/page-1)\nPostgreSQL",
                    }
                ]
            }
        ),
        1,
        0,
        0,
        1.0,
    )
    client = _FakeMcpClient([result], [])
    from mcp_adapter import LiveNotionMcpAdapter

    adapter = LiveNotionMcpAdapter(client, _scope())
    hit = McpSearchHit(
        "page-1", "DB", "https://notion.so/page-1", "", "workspace-a", "snapshot-1"
    )

    # When & then: a Markdown path cannot substitute for a server scope assertion
    with pytest.raises(McpScopeError, match="scope metadata"):
        adapter.fetch(hit)


def test_live_adapter_rejects_detail_from_another_workspace() -> None:
    # Given: a scoped hit whose server response claims another workspace
    result = McpToolExchange(
        McpToolResult.model_validate(
            {
                "structuredContent": {
                    "id": "page-1",
                    "workspace_id": "workspace-b",
                    "title": "leak",
                },
                "content": [{"type": "text", "text": "leak"}],
            }
        ),
        1,
        0,
        0,
        1.0,
    )
    client = _FakeMcpClient([result], [])
    from mcp_adapter import LiveNotionMcpAdapter

    adapter = LiveNotionMcpAdapter(client, _scope())
    hit = McpSearchHit(
        "page-1", "DB", "https://notion.so/page-1", "", "workspace-a", "snapshot-1"
    )

    # When & then: the detail cannot cross the workspace boundary
    with pytest.raises(McpScopeError):
        adapter.fetch(hit)


def test_live_adapter_rejects_detail_from_an_old_snapshot() -> None:
    # Given: a fetch response from a snapshot older than the active one
    result = McpToolExchange(
        McpToolResult.model_validate(
            {
                "structuredContent": {
                    "id": "page-1",
                    "workspace_id": "workspace-a",
                    "snapshot_id": "snapshot-old",
                    "title": "old",
                },
                "content": [{"type": "text", "text": "old content"}],
            }
        ),
        1,
        0,
        0,
        1.0,
    )
    client = _FakeMcpClient([result], [])
    from mcp_adapter import LiveNotionMcpAdapter

    adapter = LiveNotionMcpAdapter(client, _scope())
    hit = McpSearchHit(
        "page-1", "DB", "https://notion.so/page-1", "", "workspace-a", "snapshot-1"
    )

    # When & then: an older snapshot cannot enter the active context
    with pytest.raises(McpScopeError):
        adapter.fetch(hit)


def test_live_adapter_paginates_sub_block_detail_content() -> None:
    # Given: a page detail split between a root response and a continuation cursor
    first = McpToolExchange(
        McpToolResult.model_validate(
            {
                "structuredContent": {
                    "id": "page-1",
                    "title": "DB",
                    "has_more": True,
                    "next_cursor": "block-2",
                },
                "content": [{"type": "text", "text": "parent block"}],
            }
        ),
        1,
        0,
        0,
        1.0,
    )
    second = McpToolExchange(
        McpToolResult.model_validate(
            {
                "structuredContent": {"id": "page-1", "has_more": False},
                "content": [{"type": "text", "text": "child block"}],
            }
        ),
        1,
        0,
        0,
        1.0,
    )
    client = _FakeMcpClient([first, second], [])
    from mcp_adapter import LiveNotionMcpAdapter

    adapter = LiveNotionMcpAdapter(client, _scope())
    hit = McpSearchHit(
        "page-1", "DB", "https://notion.so/page-1", "", "workspace-a", "snapshot-1"
    )

    # When: the adapter fetches the page and its continuation blocks
    fetched = adapter.fetch(hit)

    # Then: all sub-block content and pagination accounting are preserved
    assert fetched.page.content == "parent block\n\nchild block"
    assert fetched.trace.http_requests == 2
    assert client.calls == [
        ("notion-fetch", {"url": "https://notion.so/page-1"}),
        ("notion-fetch", {"url": "https://notion.so/page-1", "cursor": "block-2"}),
    ]


def test_live_adapter_does_not_return_partial_content_after_a_sub_block_failure() -> (
    None
):
    # Given: a successful root detail followed by an MCP error for its continuation
    first = McpToolExchange(
        McpToolResult.model_validate(
            {
                "structuredContent": {
                    "id": "page-1",
                    "has_more": True,
                    "next_cursor": "block-2",
                },
                "content": [{"type": "text", "text": "partial"}],
            }
        ),
        1,
        0,
        0,
        1.0,
    )
    failure = McpToolExchange(
        McpToolResult.model_validate(
            {
                "isError": True,
                "content": [{"type": "text", "text": "continuation failed"}],
            }
        ),
        1,
        0,
        0,
        1.0,
    )
    client = _FakeMcpClient([first, failure], [])
    from mcp_adapter import LiveNotionMcpAdapter, McpAdapterError

    adapter = LiveNotionMcpAdapter(client, _scope())
    hit = McpSearchHit(
        "page-1", "DB", "https://notion.so/page-1", "", "workspace-a", "snapshot-1"
    )

    # When & then: partial content is not exposed as a successful page
    with pytest.raises(McpAdapterError, match="continuation failed"):
        adapter.fetch(hit)


def test_live_search_discards_a_hit_claiming_another_workspace() -> None:
    # Given: a search response that assigns an allowed page ID to another workspace
    result = McpToolExchange(
        McpToolResult.model_validate(
            {
                "structuredContent": {
                    "results": [
                        {
                            "id": "page-1",
                            "workspace_id": "workspace-b",
                            "title": "leak",
                            "snippet": "secret",
                        }
                    ]
                }
            }
        ),
        1,
        0,
        0,
        1.0,
    )
    client = _FakeMcpClient([result], [])
    from mcp_adapter import LiveNotionMcpAdapter

    adapter = LiveNotionMcpAdapter(client, _scope())

    # When: the live adapter normalizes the search response
    search = adapter.search("secret", limit=1)

    # Then: response metadata cannot widen the connected workspace scope
    assert search.hits == ()


def test_live_search_discards_a_hit_without_active_snapshot_proof() -> None:
    # Given: an allowed page ID whose response omits the active snapshot identity
    result = McpToolExchange(
        McpToolResult.model_validate(
            {
                "structuredContent": {
                    "results": [
                        {
                            "id": "page-1",
                            "url": "https://notion.so/page-1",
                            "title": "unproven",
                            "snippet": "secret",
                        }
                    ]
                }
            }
        ),
        1,
        0,
        0,
        1.0,
    )
    client = _FakeMcpClient([result], [])
    from mcp_adapter import LiveNotionMcpAdapter

    adapter = LiveNotionMcpAdapter(client, _scope())

    # When: the live adapter normalizes the search result
    search = adapter.search("secret", limit=1)

    # Then: an active snapshot cannot be inferred from missing response metadata
    assert search.hits == ()


def test_live_fetch_rejects_more_content_without_a_continuation_cursor() -> None:
    # Given: a detail response that claims more blocks but supplies no cursor
    result = McpToolExchange(
        McpToolResult.model_validate(
            {
                "structuredContent": {
                    "id": "page-1",
                    "has_more": True,
                },
                "content": [{"type": "text", "text": "partial"}],
            }
        ),
        1,
        0,
        0,
        1.0,
    )
    client = _FakeMcpClient([result], [])
    from mcp_adapter import LiveNotionMcpAdapter

    adapter = LiveNotionMcpAdapter(client, _scope())
    hit = McpSearchHit(
        "page-1", "DB", "https://notion.so/page-1", "", "workspace-a", "snapshot-1"
    )

    # When & then: incomplete content is not exposed as a successful page
    with pytest.raises(McpAdapterError, match="continuation cursor"):
        adapter.fetch(hit)


def test_live_fetch_rejects_unresolved_block_ids_in_a_complete_response() -> None:
    # Given: a response that explicitly reports blocks that were not returned
    result = McpToolExchange(
        McpToolResult.model_validate(
            {
                "structuredContent": {
                    "id": "page-1",
                    "unknown_block_ids": ["block-2"],
                    "has_more": False,
                },
                "content": [{"type": "text", "text": "partial"}],
            }
        ),
        1,
        0,
        0,
        1.0,
    )
    client = _FakeMcpClient([result], [])
    from mcp_adapter import LiveNotionMcpAdapter

    adapter = LiveNotionMcpAdapter(client, _scope())
    hit = McpSearchHit(
        "page-1", "DB", "https://notion.so/page-1", "", "workspace-a", "snapshot-1"
    )

    # When & then: unresolved blocks prevent partial page grounding
    with pytest.raises(McpAdapterError, match="unresolved block"):
        adapter.fetch(hit)


def test_live_search_discards_an_external_markdown_link_with_an_allowed_page_id() -> (
    None
):
    # Given: a Markdown link that embeds an allowed page ID on an untrusted host
    result = McpToolExchange(
        McpToolResult.model_validate(
            {
                "content": [
                    {
                        "type": "text",
                        "text": "[Allowed](https://evil.example/page-1)",
                    }
                ]
            }
        ),
        1,
        0,
        0,
        1.0,
    )
    client = _FakeMcpClient([result], [])
    from mcp_adapter import LiveNotionMcpAdapter

    adapter = LiveNotionMcpAdapter(client, _scope())

    # When: the live adapter normalizes the search result
    search = adapter.search("Allowed", limit=1)

    # Then: an unsafe URL is not exposed before the fetch boundary
    assert search.hits == ()


def test_live_fetch_rejects_an_out_of_scope_hit_before_network_call() -> None:
    # Given: a model or caller supplies a page ID outside the active allowlist
    client = _FakeMcpClient([], [])
    from mcp_adapter import LiveNotionMcpAdapter

    adapter = LiveNotionMcpAdapter(client, _scope())
    outside = McpSearchHit(
        "page-3", "Outside", "https://notion.so/page-3", "", "workspace-a", "snapshot-1"
    )

    # When & then: scope is checked before the Notion endpoint is called
    with pytest.raises(McpScopeError):
        adapter.fetch(outside)
    assert client.calls == []


def test_live_fetch_rejects_a_non_notion_url_before_network_call() -> None:
    # Given: an allowed page ID paired with a URL outside the Notion host boundary
    client = _FakeMcpClient([], [])
    from mcp_adapter import LiveNotionMcpAdapter

    adapter = LiveNotionMcpAdapter(client, _scope())
    malicious_url = McpSearchHit(
        "page-1",
        "Allowed ID",
        "https://evil.example/page-1",
        "",
        "workspace-a",
        "snapshot-1",
    )

    # When & then: URL validation blocks the call before the access token is sent
    with pytest.raises(McpScopeError):
        adapter.fetch(malicious_url)
    assert client.calls == []


def test_validated_fetch_enforces_scope_even_for_a_non_scoped_adapter() -> None:
    # Given: a generic adapter that would otherwise accept every page identity
    class _PermissiveAdapter:
        def __init__(self) -> None:
            self.calls: list[McpSearchHit] = []

        def search(self, query: str, limit: int) -> McpSearchResult:
            raise AssertionError("search must not be called")

        def fetch(self, hit: McpSearchHit) -> McpFetchResult:
            self.calls.append(hit)
            return McpFetchResult(
                _page(hit.page_id, "unsafe"),
                McpToolTrace("fetch", "fake", 0.0, 0, 0, 0, 1),
            )

    adapter = _PermissiveAdapter()
    call = validate_nim_tool_call(
        NimToolCall(
            id="fetch-1",
            function=NimFunctionCall(name="notion-fetch", arguments='{"id":"page-3"}'),
        )
    )

    # When & then: the validated tool boundary still enforces the page allowlist
    with pytest.raises(McpScopeError):
        execute_validated_tool_call(call, adapter, _scope())
    assert adapter.calls == []
