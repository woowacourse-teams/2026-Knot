#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.14"
# dependencies = ["httpx2[http2,brotli,zstd]", "pydantic", "pydantic-settings", "pytest"]
# ///

# ─── How to run ───
# 1. Install uv (if not installed):
#      curl -LsSf https://astral.sh/uv/install.sh | sh
# 2. Run directly:
#      uv run --with httpx2 --with pydantic --with pydantic-settings --with pytest pytest tools/llm-benchmark/test_mcp_transport.py
# ──────────────────

from __future__ import annotations

import json
from collections.abc import Iterator
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread
from typing import ClassVar

import pytest
from mcp_models import JsonObject, McpSettings
from mcp_transport import McpHttpClient, McpHttpError, McpProtocolError


class _McpHandler(BaseHTTPRequestHandler):
    requests: ClassVar[list[tuple[str, dict[str, str], JsonObject | None]]] = []
    call_count: ClassVar[int] = 0
    retry_once: ClassVar[bool] = False
    auth_failure: ClassVar[bool] = False

    def do_POST(self) -> None:
        length = int(self.headers.get("Content-Length", "0"))
        request_body = json.loads(self.rfile.read(length)) if length else None
        headers = dict(self.headers)
        headers.pop("Authorization", None)
        self.requests.append((self.headers.get("Mcp-Method", ""), headers, request_body))
        if self.auth_failure:
            self._respond_error(401, "Authorization: Bearer test-token")
            return
        method = request_body.get("method") if isinstance(request_body, dict) else None
        if method == "initialize":
            self._respond_json(
                {"jsonrpc": "2.0", "id": request_body.get("id"), "result": {"protocolVersion": "2025-11-25"}},
                session_id="session-1",
            )
            return
        if method == "notifications/initialized":
            self.send_response(202)
            self.end_headers()
            return
        if method != "tools/call":
            self._respond_json({"jsonrpc": "2.0", "id": request_body.get("id"), "error": {"code": -32601, "message": "unknown"}})
            return
        params = request_body.get("params", {})
        if isinstance(params, dict) and params.get("name") == "unknown-tool":
            self._respond_json(
                {"jsonrpc": "2.0", "id": request_body.get("id"), "error": {"code": -32601, "message": "unknown tool"}}
            )
            return
        type(self).call_count += 1
        if self.retry_once and self.call_count == 1:
            self.send_response(429)
            self.send_header("Retry-After", "0")
            self.end_headers()
            return
        body = (
            b"event: message\n"
            b'data: {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"live result"}]}}\n\n'
        )
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _respond_json(self, payload: JsonObject, session_id: str | None = None) -> None:
        body = json.dumps(payload).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        if session_id is not None:
            self.send_header("Mcp-Session-Id", session_id)
        self.end_headers()
        self.wfile.write(body)

    def _respond_error(self, status_code: int, detail: str) -> None:
        body = detail.encode()
        self.send_response(status_code)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args: str) -> None:
        return


@pytest.fixture()
def mcp_server() -> Iterator[tuple[str, ThreadingHTTPServer]]:
    _McpHandler.requests = []
    _McpHandler.call_count = 0
    _McpHandler.retry_once = False
    _McpHandler.auth_failure = False
    server = ThreadingHTTPServer(("127.0.0.1", 0), _McpHandler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_port}/mcp", server
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=1)


def test_http_client_parses_sse_and_carries_session_and_tool_headers(
    mcp_server: tuple[str, ThreadingHTTPServer],
) -> None:
    # Given: a Streamable HTTP server that initializes a session and responds with SSE
    endpoint, _server = mcp_server
    client = McpHttpClient(McpSettings(endpoint_url=endpoint, access_token="test-token", retry_backoff_s=0))

    # When: one read-only tool is called
    try:
        exchange = client.call_tool("notion-search", {"query": "PostgreSQL"})
    finally:
        client.close()

    # Then: the result and protocol accounting prove the full handshake and tool call
    assert exchange.result.content[0].text == "live result"
    assert exchange.http_requests == 3
    assert exchange.retry_count == 0
    assert _McpHandler.requests[2][0] == "tools/call"
    assert _McpHandler.requests[2][1]["Mcp-Session-Id"] == "session-1"
    assert _McpHandler.requests[2][1]["Mcp-Name"] == "notion-search"


def test_http_client_retries_rate_limit_and_reports_it(
    mcp_server: tuple[str, ThreadingHTTPServer],
) -> None:
    # Given: a read-only MCP call that is rate-limited once
    endpoint, _server = mcp_server
    _McpHandler.retry_once = True
    client = McpHttpClient(
        McpSettings(endpoint_url=endpoint, access_token="test-token", max_retries=1, retry_backoff_s=0)
    )

    # When: the client calls the tool
    try:
        exchange = client.call_tool("notion-fetch", {"id": "page-1"})
    finally:
        client.close()

    # Then: the retry is visible without exposing the access token
    assert exchange.result.content[0].text == "live result"
    assert exchange.http_requests == 4
    assert exchange.retry_count == 1
    assert exchange.rate_limit_count == 1
    assert all("test-token" not in str(request) for request in _McpHandler.requests)


def test_http_client_surfaces_json_rpc_errors_as_protocol_errors(
    mcp_server: tuple[str, ThreadingHTTPServer],
) -> None:
    # Given: a client connected to the stub server
    endpoint, _server = mcp_server
    _McpHandler.retry_once = False
    client = McpHttpClient(McpSettings(endpoint_url=endpoint, access_token="test-token", retry_backoff_s=0))

    # When & then: the successful stub path is intentionally read-only and unknown calls are rejected locally
    with pytest.raises(McpProtocolError):
        client.call_tool("unknown-tool", {})
    client.close()


def test_http_client_redacts_bearer_token_when_mcp_rejects_authentication(
    mcp_server: tuple[str, ThreadingHTTPServer],
) -> None:
    # Given: an MCP server that rejects the request with a token-shaped detail
    endpoint, _server = mcp_server
    _McpHandler.auth_failure = True
    client = McpHttpClient(McpSettings(endpoint_url=endpoint, access_token="test-token", retry_backoff_s=0))

    # When & then: the transport exposes the status without leaking the credential
    with pytest.raises(McpHttpError) as caught:
        client.call_tool("notion-search", {"query": "PostgreSQL"})
    client.close()

    assert caught.value.status_code == 401
    assert "test-token" not in str(caught.value)
    assert "Bearer [redacted]" in str(caught.value)
