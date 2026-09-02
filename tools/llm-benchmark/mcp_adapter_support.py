"""Small deterministic helpers for assembling MCP benchmark observations."""

from __future__ import annotations

import time
from collections.abc import Iterable
from pathlib import Path

from benchmark_core import Chunk, ContextPack, tokenize
from mcp_models import McpPage, McpToolTrace
from mcp_transport import McpToolExchange


def snippet(content: str, query_terms: set[str]) -> str:
    """Keep a short lexical line as the replay search preview."""
    return next((line.strip() for line in content.splitlines() if query_terms & set(tokenize(line))), content[:240])


def merge_page(previous: McpPage | None, current: McpPage) -> McpPage:
    """Merge metadata across cursor pages while leaving content assembly to the caller."""
    return current if previous is None else McpPage(
        current.page_id,
        current.title or previous.title,
        current.url or previous.url,
        previous.content,
        current.workspace_id,
        current.snapshot_id,
        current.parent_page_id or previous.parent_page_id,
        current.last_edited_time or previous.last_edited_time,
    )


def render_context(pages: tuple[McpPage, ...]) -> ContextPack:
    """Render fetched pages with source URLs and tool-call count metadata."""
    chunks = tuple(Chunk(Path(page.url), page.title, page.content, 0.0) for page in pages)
    return ContextPack(
        "\n\n".join(_render_chunk(chunk) for chunk in chunks),
        tuple(page.url for page in pages),
        len(pages),
        len(pages) + 1,
    )


def trace(operation: str, tool_name: str, started: float, page_count: int) -> McpToolTrace:
    """Create a zero-network trace for the deterministic replay adapter."""
    return McpToolTrace(operation, tool_name, (time.perf_counter() - started) * 1000, 0, 0, 0, page_count)


def combined_trace(
    operation: str,
    tool_name: str,
    started: float,
    exchanges: Iterable[McpToolExchange],
    page_count: int,
) -> McpToolTrace:
    """Aggregate transport counters across paginated MCP calls."""
    values = tuple(exchanges)
    return McpToolTrace(
        operation,
        tool_name,
        (time.perf_counter() - started) * 1000,
        sum(value.http_requests for value in values),
        sum(value.retry_count for value in values),
        sum(value.rate_limit_count for value in values),
        page_count,
    )


def _render_chunk(chunk: Chunk) -> str:
    return f"## {chunk.title}\nsource_path: {chunk.path}\n\n{chunk.content}"
