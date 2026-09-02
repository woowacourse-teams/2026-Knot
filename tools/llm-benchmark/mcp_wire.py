"""JSON-RPC and SSE parsing helpers for the MCP transport."""

from __future__ import annotations

import re
from collections.abc import Iterable

import httpx2
from mcp_errors import McpProtocolError
from mcp_models import McpRpcResponse


def parse_response(response: httpx2.Response) -> McpRpcResponse:
    """Parse one JSON or Server-Sent Events MCP response envelope."""
    if "text/event-stream" in response.headers.get("Content-Type", ""):
        return _parse_sse(response.text)
    try:
        return McpRpcResponse.model_validate_json(response.text)
    except ValueError as error:
        raise McpProtocolError("MCP response is not valid JSON-RPC") from error


def safe_detail(detail: str, secrets: Iterable[str] = ()) -> str:
    """Truncate error details and redact bearer credentials."""
    return redact_secrets(detail[:500], secrets)


def redact_secrets(detail: str, secrets: Iterable[str] = ()) -> str:
    """Redact credentials without truncating successful document payloads."""
    redacted = re.sub(r"Bearer\s+[^\s\"']+", "Bearer [redacted]", detail)
    for secret in secrets:
        if secret:
            redacted = redacted.replace(secret, "[redacted]")
    return redacted


def _parse_sse(body: str) -> McpRpcResponse:
    for event in re.split(r"\r?\n\r?\n", body):
        data_lines = [
            line.removeprefix("data:").lstrip()
            for line in event.splitlines()
            if line.startswith("data:")
        ]
        payload = "\n".join(data_lines).strip()
        if not payload or payload == "[DONE]":
            continue
        try:
            parsed = McpRpcResponse.model_validate_json(payload)
        except ValueError as error:
            raise McpProtocolError("MCP SSE data is not valid JSON-RPC") from error
        if (
            parsed.id is not None
            or parsed.result is not None
            or parsed.error is not None
        ):
            return parsed
    raise McpProtocolError("MCP SSE response contained no JSON-RPC data")
