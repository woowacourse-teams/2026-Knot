from __future__ import annotations

import shutil
from datetime import timedelta

import anyio
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client
from mcp.shared.exceptions import McpError
from pydantic import ValidationError

from notion_models import ToolResponse
from settings import AssistantFailure, NotionLookup, SCRIPT_DIR


async def lookup_via_mcp(question: str, search_queries: tuple[str, ...] = ()) -> NotionLookup:
    uv = shutil.which("uv")
    if uv is None:
        raise AssistantFailure("notion_mcp_uv_missing")
    parameters = StdioServerParameters(command=uv, args=["run", "--python", "3.11", "--script", str(SCRIPT_DIR / "notion_mcp.py")])
    parsed: ToolResponse | None = None
    failure_code: str | None = None
    try:
        async with stdio_client(parameters) as (read, write):
            async with ClientSession(read, write, read_timeout_seconds=timedelta(seconds=120)) as session:
                await session.initialize()
                result = await session.call_tool("search_knot", {"query": question, "search_queries": list(search_queries)})
                if result.isError:
                    failure_code = "notion_mcp_tool_failed"
                else:
                    parsed = ToolResponse.model_validate(result.structuredContent)
    except* (McpError, anyio.EndOfStream, anyio.BrokenResourceError, OSError):
        failure_code = "notion_mcp_connection_failed"
    except* ValidationError:
        failure_code = "notion_mcp_response_invalid"
    if failure_code is not None:
        raise AssistantFailure(failure_code)
    if parsed is None:
        raise AssistantFailure("notion_mcp_empty_response")
    if parsed.error_code is not None:
        raise AssistantFailure(parsed.error_code)
    if parsed.lookup is None:
        raise AssistantFailure("notion_mcp_empty_response")
    return parsed.lookup
