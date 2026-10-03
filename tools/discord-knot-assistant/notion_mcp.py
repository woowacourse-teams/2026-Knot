# /// script
# requires-python = ">=3.11,<3.12"
# dependencies = ["mcp>=1.12,<2", "httpx2[http2,brotli,zstd]", "keyring>=25,<27", "pydantic-settings>=2.8,<3"]
# ///
# ─── How to run ───
#   uv run --script tools/discord-knot-assistant/notion_mcp.py

from __future__ import annotations

from typing import Annotated, assert_never

import httpx2
from mcp.server.fastmcp import FastMCP
from mcp.types import ToolAnnotations
from pydantic import Field, StringConstraints, ValidationError

from notion_api import NotionReader, notion_transport
from notion_credentials import load_notion_token
from notion_lookup import ScopedLookup
from notion_models import ToolOperation, ToolResponse, parse_notion_id
from settings import AssistantFailure

mcp = FastMCP("Knot Notion Read Only", log_level="ERROR")
READ_ONLY = ToolAnnotations(readOnlyHint=True, destructiveHint=False, idempotentHint=True, openWorldHint=True)


async def execute(operation: ToolOperation, value: str, search_queries: tuple[str, ...] = ()) -> ToolResponse:
    try:
        token = load_notion_token()
        async with httpx2.AsyncClient(
            base_url="https://api.notion.com/v1/",
            headers={"Authorization": f"Bearer {token}", "Notion-Version": "2025-09-03"},
            transport=notion_transport(),
            timeout=httpx2.Timeout(connect=5, read=20, write=10, pool=10), follow_redirects=False,
        ) as http:
            lookup = ScopedLookup(NotionReader(http))
            match operation:
                case ToolOperation.SEARCH:
                    await lookup.search(value[:2000], search_queries)
                case ToolOperation.PAGE:
                    await lookup.read_page(parse_notion_id(value))
                case ToolOperation.DATABASE:
                    await lookup.read_database(parse_notion_id(value))
                case unreachable:
                    assert_never(unreachable)
            return ToolResponse(lookup=lookup.result())
    except AssistantFailure as error:
        return ToolResponse(error_code=error.code)
    except httpx2.TimeoutException:
        return ToolResponse(error_code="notion_timeout")
    except httpx2.HTTPError:
        return ToolResponse(error_code="notion_connection_failed")
    except ValidationError:
        return ToolResponse(error_code="notion_response_invalid")


@mcp.tool(description="Search Notion page titles by keywords, read up to five verified Knot descendants. Not a full-text search.", annotations=READ_ONLY)
async def search_knot(
    query: str,
    search_queries: Annotated[list[Annotated[str, StringConstraints(min_length=1, max_length=40)]], Field(max_length=4)] | None = None,
) -> ToolResponse:
    return await execute(ToolOperation.SEARCH, query, tuple(search_queries or ()))


@mcp.tool(description="Read a page and nested blocks after verifying its parent chain reaches Knot. Accept a UUID only.", annotations=READ_ONLY)
async def read_knot_page(page_id: str) -> ToolResponse:
    return await execute(ToolOperation.PAGE, page_id)


@mcp.tool(description="Read Knot database data sources and up to eight rows per data source, including page properties and blocks.", annotations=READ_ONLY)
async def read_knot_database(database_id: str) -> ToolResponse:
    return await execute(ToolOperation.DATABASE, database_id)


if __name__ == "__main__":
    mcp.run(transport="stdio")
