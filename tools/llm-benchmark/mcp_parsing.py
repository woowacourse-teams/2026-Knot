"""Normalize untrusted MCP content into scoped Notion page primitives."""

from __future__ import annotations

import re
from urllib.parse import urlsplit

from mcp_adapter_errors import McpScopeError
from mcp_models import (
    JsonObject,
    JsonValue,
    McpPage,
    McpScope,
    McpSearchHit,
    McpToolResult,
)
from pydantic import TypeAdapter, ValidationError

_MARKDOWN_LINK = re.compile(r"\[([^\]]+)\]\((https?://[^)\s]+)\)")
_PAGE_URL = re.compile(
    r"https?://(?:www\.)?(?:notion\.so|notion\.site|notion\.com)/[^\s)\]>]+"
)
_PAGE_ID = re.compile(
    r"(?:[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}|[0-9a-f]{32})",
    re.IGNORECASE,
)
_NOTION_HOSTS = frozenset({"notion.com", "notion.site", "notion.so"})
_JSON_VALUE = TypeAdapter(JsonValue)


def search_hits(result: McpToolResult, scope: McpScope) -> tuple[McpSearchHit, ...]:
    """Extract structured and Markdown-linked pages that are in the scope allowlist."""
    hits: list[McpSearchHit] = []
    seen: set[str] = set()
    for record in result_records(result):
        page_id = string(record, "id", "page_id", "pageId")
        url = string(record, "url", "page_url", "pageUrl")
        if page_id is None and url is not None:
            page_id = page_id_from_url(url)
        if page_id is None:
            continue
        url = url or f"https://notion.so/{page_id}"
        if not is_safe_notion_url(url) or not page_id_matches_url(page_id, url):
            continue
        normalized_id = normalize_page_id(page_id)
        if normalized_id not in scope.allowed_page_ids or normalized_id in seen:
            continue
        workspace_id = (
            string(record, "workspace_id", "workspaceId") or scope.workspace_id
        )
        snapshot_id = (
            string(record, "snapshot_id", "snapshotId") or scope.active_snapshot_id
        )
        if not scope.permits(McpPage(page_id, "", url, "", workspace_id, snapshot_id)):
            continue
        hits.append(
            McpSearchHit(
                page_id,
                string(record, "title", "name") or page_id,
                url,
                string(record, "snippet", "text", "description") or "",
                workspace_id,
                snapshot_id,
                string(record, "last_edited_time", "lastEditedTime"),
            )
        )
        seen.add(normalized_id)
    for content in text_content(result):
        for title, url in links(content):
            page_id = page_id_from_url(url)
            if page_id is None:
                continue
            if not is_safe_notion_url(url) or not page_id_matches_url(page_id, url):
                continue
            normalized_id = normalize_page_id(page_id)
            if normalized_id in scope.allowed_page_ids and normalized_id not in seen:
                hits.append(
                    McpSearchHit(
                        page_id,
                        title,
                        url,
                        content,
                        scope.workspace_id,
                        scope.active_snapshot_id,
                    )
                )
                seen.add(normalized_id)
    return tuple(hits)


def page_from_result(
    result: McpToolResult, hit: McpSearchHit, scope: McpScope
) -> McpPage:
    """Extract one page from a fetch result and enforce the connected scope."""
    record = next(
        (
            candidate
            for candidate in result_records(result)
            if string(candidate, "id", "page_id", "pageId") is not None
        ),
        {},
    )
    page = McpPage(
        string(record, "id", "page_id", "pageId") or hit.page_id,
        string(record, "title", "name") or heading(result) or hit.title,
        string(record, "url", "page_url", "pageUrl") or hit.url,
        "\n\n".join(page_text_content(result))
        or string(record, "content", "text")
        or hit.snippet,
        string(record, "workspace_id", "workspaceId")
        or hit.workspace_id
        or scope.workspace_id,
        string(record, "snapshot_id", "snapshotId")
        or hit.snapshot_id
        or scope.active_snapshot_id,
        string(record, "parent_id", "parentPageId"),
        string(record, "last_edited_time", "lastEditedTime") or hit.last_edited_time,
    )
    if not scope.permits(page):
        raise McpScopeError(f"page {page.page_id!r} is outside the active scope")
    if not is_safe_notion_url(page.url):
        raise McpScopeError(f"page {page.page_id!r} has an unsafe URL")
    if normalize_page_id(page.page_id) != normalize_page_id(hit.page_id):
        raise McpScopeError(f"page {page.page_id!r} has a mismatched identity")
    if not page_id_matches_url(page.page_id, page.url):
        raise McpScopeError(f"page {page.page_id!r} has a mismatched URL")
    return page


def pagination(result: McpToolResult) -> tuple[bool, str | None]:
    """Read optional cursor pagination metadata from structured MCP content."""
    for record in result_records(result):
        has_more = record.get("has_more")
        next_cursor = record.get("next_cursor")
        if isinstance(has_more, bool):
            return has_more, next_cursor if isinstance(next_cursor, str) else None
    return False, None


def text_content(result: McpToolResult) -> tuple[str, ...]:
    """Return text blocks without passing opaque protocol values downstream."""
    return tuple(block.text for block in result.content if block.text)


def result_records(result: McpToolResult) -> tuple[JsonObject, ...]:
    """Read structured records and JSON objects embedded in text content blocks."""
    found = list(records(result.structured_content))
    for text in text_content(result):
        value = _json_text(text)
        if isinstance(value, dict):
            found.extend(records(value))
        elif isinstance(value, list):
            for item in value:
                if isinstance(item, dict):
                    found.extend(records(item))
    return tuple(found)


def page_text_content(result: McpToolResult) -> tuple[str, ...]:
    """Extract page prose from Markdown or JSON text payloads."""
    content: list[str] = []
    for text in text_content(result):
        value = _json_text(text)
        if value is None:
            content.append(text)
            continue
        content.extend(_content_strings(value))
    return tuple(content)


def _json_text(text: str) -> JsonValue | None:
    try:
        return _JSON_VALUE.validate_json(text)
    except (ValidationError, ValueError):
        return None


def _content_strings(value: JsonValue) -> tuple[str, ...]:
    match value:
        case dict():
            direct = tuple(
                child
                for key in ("markdown", "content", "text", "body")
                if isinstance(child := value.get(key), str) and child
            )
            return direct or tuple(
                child for nested in value.values() for child in _content_strings(nested)
            )
        case list():
            return tuple(
                child for nested in value for child in _content_strings(nested)
            )
        case str():
            return (value,)
        case int() | float() | bool() | None:
            return ()


def records(value: JsonObject | None) -> tuple[JsonObject, ...]:
    """Walk structured content and return every JSON object in stable traversal order."""
    if value is None:
        return ()
    found: list[JsonObject] = []
    pending: list[JsonValue] = [value]
    while pending:
        current = pending.pop()
        match current:
            case dict():
                normalized = {
                    key: child for key, child in current.items() if isinstance(key, str)
                }
                found.append(normalized)
                pending.extend(reversed(tuple(normalized.values())))
            case list():
                pending.extend(reversed(current))
            case str() | int() | float() | bool() | None:
                continue
            case unreachable:
                from typing import assert_never

                assert_never(unreachable)
    return tuple(found)


def links(text: str) -> tuple[tuple[str, str], ...]:
    """Extract Notion links from Markdown or plain MCP text blocks."""
    markdown = tuple(_MARKDOWN_LINK.findall(text))
    known_urls = {url for _, url in markdown}
    plain = tuple(
        (url.rsplit("/", 1)[-1], url)
        for url in _PAGE_URL.findall(text)
        if url not in known_urls
    )
    return markdown + plain


def string(record: JsonObject, *keys: str) -> str | None:
    """Read the first non-empty string field from a structured result object."""
    return next(
        (value for key in keys if isinstance(value := record.get(key), str) and value),
        None,
    )


def page_id_from_url(url: str) -> str | None:
    """Extract a UUID page ID or a final URL segment from a Notion link."""
    match = _PAGE_ID.search(url)
    return (
        match.group(0)
        if match is not None
        else url.rstrip("/").rsplit("/", 1)[-1] or None
    )


def is_safe_notion_url(url: str) -> bool:
    """Allow only HTTPS URLs hosted by Notion or its supported subdomains."""
    parsed = urlsplit(url)
    host = parsed.hostname
    try:
        port = parsed.port
    except ValueError:
        return False
    return (
        parsed.scheme == "https"
        and host is not None
        and parsed.username is None
        and parsed.password is None
        and not parsed.query
        and not parsed.fragment
        and port in (None, 443)
        and any(host == base or host.endswith(f".{base}") for base in _NOTION_HOSTS)
    )


def page_id_matches_url(page_id: str, url: str) -> bool:
    """Return whether a page URL identifies the same allowlisted page."""
    url_page_id = page_id_from_url(url)
    return url_page_id is not None and normalize_page_id(
        url_page_id
    ) == normalize_page_id(page_id)


def normalize_page_id(page_id: str) -> str:
    """Normalize UUID formatting for allowlist comparisons."""
    return page_id.casefold().replace("-", "")


def heading(result: McpToolResult) -> str | None:
    """Use the first Markdown H1 as a fallback page title."""
    content = page_text_content(result)
    return (
        next(
            (
                line.removeprefix("# ").strip()
                for line in content[0].splitlines()
                if line.startswith("# ")
            ),
            None,
        )
        if content
        else None
    )
