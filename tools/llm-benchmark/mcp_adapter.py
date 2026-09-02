"""Scope-aware replay and live adapters for read-only Notion MCP tools."""

from __future__ import annotations

import re
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol, assert_never

from benchmark_core import ContextPack, Document, tokenize
from mcp_adapter_errors import McpAdapterError, McpScopeError
from mcp_adapter_support import (
    combined_trace,
    merge_page,
    render_context,
    snippet,
    trace,
)
from mcp_models import (
    FetchToolArguments,
    JsonObject,
    McpFetchResult,
    McpPage,
    McpReadAdapter,
    McpScope,
    McpSearchHit,
    McpSearchResult,
    McpToolName,
    McpToolResult,
    McpToolTrace,
    SearchToolArguments,
    ValidatedToolCall,
)
from mcp_parsing import (
    is_safe_notion_url,
    normalize_page_id,
    page_from_result,
    page_id_from_url,
    page_id_matches_url,
    pagination,
    search_hits,
    text_content,
)
from mcp_transport import McpToolExchange

_PAGE_ID = re.compile(
    r"(?:[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}|[0-9a-f]{32})",
    re.IGNORECASE,
)


class McpToolCaller(Protocol):
    """The transport dependency required by the live adapter."""

    def call_tool(self, name: str, arguments: JsonObject) -> McpToolExchange:
        """Call one read-only MCP tool."""
        ...


@dataclass(frozen=True, slots=True)
class McpContextTiming:
    """Context pack plus every MCP operation used to build it."""

    context: ContextPack
    traces: tuple[McpToolTrace, ...]


class ReplayMcpAdapter:
    """Read-only MCP-shaped adapter over one normalized snapshot."""

    def __init__(self, pages: tuple[McpPage, ...], scope: McpScope) -> None:
        self._scope = scope
        self._pages = {
            normalize_page_id(page.page_id): page
            for page in pages
            if scope.permits(page)
        }

    @classmethod
    def from_documents(
        cls,
        documents: tuple[Document, ...],
        workspace_id: str,
        snapshot_id: str,
        allowed_page_ids: frozenset[str],
    ) -> ReplayMcpAdapter:
        """Build replay pages from Markdown documents with stable source IDs."""
        scope = McpScope(workspace_id, snapshot_id, allowed_page_ids)
        pages = tuple(
            _document_page(document, workspace_id, snapshot_id)
            for document in documents
            if _page_id_from_path(document.path)
        )
        return cls(pages, scope)

    def search(self, query: str, limit: int) -> McpSearchResult:
        """Return deterministic lexical hits from the active replay corpus."""
        started = time.perf_counter()
        query_terms = set(tokenize(query))
        scored = sorted(
            (
                (len(query_terms & set(tokenize(f"{page.title} {page.content}"))), page)
                for page in self._pages.values()
            ),
            key=lambda item: (-item[0], item[1].page_id),
        )
        hits = tuple(
            _hit(page, snippet(page.content, query_terms))
            for score, page in scored[: max(limit, 0)]
            if score > 0
        )
        return McpSearchResult(
            hits, trace("search", "mcp-replay", started, 1 if limit > 0 else 0)
        )

    def fetch(self, hit: McpSearchHit) -> McpFetchResult:
        """Fetch one page only when its identity is inside the active replay scope."""
        started = time.perf_counter()
        page = self._pages.get(normalize_page_id(hit.page_id))
        if page is None or not self._scope.permits(page):
            raise McpScopeError(f"page {hit.page_id!r} is outside the active scope")
        return McpFetchResult(page, trace("fetch", "mcp-replay", started, 1))


class LiveNotionMcpAdapter:
    """Read-only adapter that normalizes hosted Notion MCP tool responses."""

    def __init__(
        self,
        client: McpToolCaller,
        scope: McpScope,
        *,
        search_tool: str = "notion-search",
        fetch_tool: str = "notion-fetch",
    ) -> None:
        if search_tool != McpToolName.SEARCH.value:
            raise ValueError("only notion-search is allowed")
        if fetch_tool != McpToolName.FETCH.value:
            raise ValueError("only notion-fetch is allowed")
        self._client = client
        self._scope = scope
        self._search_tool = search_tool
        self._fetch_tool = fetch_tool

    def search(self, query: str, limit: int) -> McpSearchResult:
        """Search MCP result pages needed to fill the requested hit limit."""
        started = time.perf_counter()
        hits: list[McpSearchHit] = []
        seen_ids: set[str] = set()
        cursor: str | None = None
        traces: list[McpToolExchange] = []
        seen_cursors: set[str] = set()
        while len(hits) < max(limit, 0):
            arguments: JsonObject = {"query": query}
            if cursor is not None:
                arguments["cursor"] = cursor
            exchange = self._client.call_tool(self._search_tool, arguments)
            _ensure_success(exchange.result)
            traces.append(exchange)
            for hit in search_hits(exchange.result, self._scope):
                normalized_id = normalize_page_id(hit.page_id)
                if normalized_id not in seen_ids:
                    hits.append(hit)
                    seen_ids.add(normalized_id)
            has_more, next_cursor = pagination(exchange.result)
            if not has_more or next_cursor is None or next_cursor in seen_cursors:
                break
            seen_cursors.add(next_cursor)
            cursor = next_cursor
        return McpSearchResult(
            tuple(hits[:limit]),
            combined_trace("search", self._search_tool, started, traces, len(traces)),
        )

    def fetch(self, hit: McpSearchHit) -> McpFetchResult:
        """Fetch page content and any cursor-based continuation from Notion MCP."""
        if (
            not self._scope.permits(
                McpPage(
                    hit.page_id,
                    hit.title,
                    hit.url,
                    hit.snippet,
                    hit.workspace_id,
                    hit.snapshot_id,
                )
            )
            or not is_safe_notion_url(hit.url)
            or not page_id_matches_url(hit.page_id, hit.url)
        ):
            raise McpScopeError(f"page {hit.page_id!r} is outside the active scope")
        started = time.perf_counter()
        exchanges: list[McpToolExchange] = []
        cursor: str | None = None
        seen_cursors: set[str] = set()
        content: list[str] = []
        page: McpPage | None = None
        while True:
            arguments: JsonObject = {"url": hit.url}
            if cursor is not None:
                arguments["cursor"] = cursor
            exchange = self._client.call_tool(self._fetch_tool, arguments)
            _ensure_success(exchange.result)
            exchanges.append(exchange)
            page = merge_page(page, page_from_result(exchange.result, hit, self._scope))
            content.extend(text_content(exchange.result))
            has_more, next_cursor = pagination(exchange.result)
            if not has_more or next_cursor is None or next_cursor in seen_cursors:
                break
            seen_cursors.add(next_cursor)
            cursor = next_cursor
        if page is None:
            raise McpAdapterError(f"MCP fetch returned no page for {hit.page_id!r}")
        if content:
            page = McpPage(
                page.page_id,
                page.title,
                page.url,
                "\n\n".join(dict.fromkeys(content)),
                page.workspace_id,
                page.snapshot_id,
                page.parent_page_id,
                page.last_edited_time,
            )
        return McpFetchResult(
            page,
            combined_trace(
                "fetch", self._fetch_tool, started, exchanges, len(exchanges)
            ),
        )


def build_mcp_context(
    adapter: McpToolCaller, query: str, top_k: int
) -> McpContextTiming:
    """Run scoped search/detail calls and render only fetched pages for the model."""
    search = adapter.search(query, top_k)
    fetched = tuple(adapter.fetch(hit) for hit in search.hits[: max(top_k, 0)])
    context = render_context(tuple(item.page for item in fetched))
    return McpContextTiming(context, (search.trace, *(item.trace for item in fetched)))


def execute_validated_tool_call(
    call: ValidatedToolCall,
    adapter: McpReadAdapter,
    scope: McpScope,
    search_limit: int = 3,
) -> McpSearchResult | McpFetchResult:
    """Execute only a previously validated read-only model tool call."""
    match call.tool:
        case McpToolName.SEARCH:
            match call.arguments:
                case SearchToolArguments(query=query):
                    return adapter.search(query, search_limit)
                case unreachable:
                    assert_never(unreachable)
        case McpToolName.FETCH:
            match call.arguments:
                case FetchToolArguments(page_id=page_id, url=url):
                    resolved_page_id = page_id or page_id_from_url(url or "")
                    if resolved_page_id is None:
                        raise McpAdapterError(
                            f"fetch call {call.call_id!r} has no page identity"
                        )
                    hit = McpSearchHit(
                        resolved_page_id,
                        "",
                        url or f"https://notion.so/{resolved_page_id}",
                        "",
                        scope.workspace_id,
                        scope.active_snapshot_id,
                    )
                    if not scope.permits(
                        McpPage(
                            hit.page_id,
                            hit.title,
                            hit.url,
                            hit.snippet,
                            hit.workspace_id,
                            hit.snapshot_id,
                        )
                    ):
                        raise McpScopeError(
                            f"page {hit.page_id!r} is outside the active scope"
                        )
                    if not is_safe_notion_url(hit.url):
                        raise McpScopeError(f"page {hit.page_id!r} has an unsafe URL")
                    if not page_id_matches_url(hit.page_id, hit.url):
                        raise McpScopeError(
                            f"page {hit.page_id!r} has a mismatched identity"
                        )
                    return adapter.fetch(hit)
                case unreachable:
                    assert_never(unreachable)
        case unreachable:
            assert_never(unreachable)


def _document_page(document: Document, workspace_id: str, snapshot_id: str) -> McpPage:
    page_id = _page_id_from_path(document.path)
    return McpPage(
        page_id,
        document.title,
        f"https://notion.so/{page_id}",
        document.content,
        workspace_id,
        snapshot_id,
    )


def _page_id_from_path(path: Path) -> str:
    match = _PAGE_ID.search(path.stem)
    return match.group(0) if match is not None else ""


def _hit(page: McpPage, snippet: str) -> McpSearchHit:
    return McpSearchHit(
        page.page_id,
        page.title,
        page.url,
        snippet,
        page.workspace_id,
        page.snapshot_id,
        page.last_edited_time,
    )


def _ensure_success(result: McpToolResult) -> None:
    if result.is_error:
        detail = " ".join(text_content(result))[:200] or "tool execution failed"
        raise McpAdapterError(detail)
