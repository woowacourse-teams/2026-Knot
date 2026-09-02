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
import time
from collections.abc import Iterator
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread
from typing import ClassVar

import pytest
from mcp_models import JsonObject, McpSettings, McpToolResult
from mcp_transport import (
    McpHttpClient,
    McpHttpError,
    McpProtocolError,
    McpTransportError,
    _redact_tool_result,
)


class _McpHandler(BaseHTTPRequestHandler):
    requests: ClassVar[list[tuple[str, dict[str, str], JsonObject | None]]] = []
    call_count: ClassVar[int] = 0
    retry_once: ClassVar[bool] = False
    auth_failure: ClassVar[bool] = False
    echo_token: ClassVar[bool] = False
    rpc_error_echo: ClassVar[bool] = False
    tool_error_echo: ClassVar[bool] = False
    invalid_initialize: ClassVar[bool] = False
    redirect_once: ClassVar[bool] = False
    slow: ClassVar[bool] = False

    def do_POST(self) -> None:
        length = int(self.headers.get("Content-Length", "0"))
        request_body = json.loads(self.rfile.read(length)) if length else None
        headers = dict(self.headers)
        headers.pop("Authorization", None)
        self.requests.append(
            (self.headers.get("Mcp-Method", ""), headers, request_body)
        )
        if self.slow:
            time.sleep(0.05)
        if self.redirect_once:
            type(self).redirect_once = False
            self.send_response(307)
            self.send_header("Location", "/redirected")
            self.end_headers()
            return
        if self.auth_failure:
            self._respond_error(401, "Authorization: Bearer test-token")
            return
        if self.echo_token:
            self._respond_error(401, "server echoed test-token")
            return
        method = request_body.get("method") if isinstance(request_body, dict) else None
        if self.invalid_initialize and method == "initialize":
            self._respond_json({"jsonrpc": "2.0", "id": request_body.get("id")})
            return
        if method == "initialize":
            self._respond_json(
                {
                    "jsonrpc": "2.0",
                    "id": request_body.get("id"),
                    "result": {"protocolVersion": "2025-11-25"},
                },
                session_id="session-1",
            )
            return
        if method == "notifications/initialized":
            self.send_response(202)
            self.end_headers()
            return
        if method != "tools/call":
            self._respond_json(
                {
                    "jsonrpc": "2.0",
                    "id": request_body.get("id"),
                    "error": {"code": -32601, "message": "unknown"},
                }
            )
            return
        params = request_body.get("params", {})
        if self.rpc_error_echo:
            self._respond_json(
                {
                    "jsonrpc": "2.0",
                    "id": request_body.get("id"),
                    "error": {"code": -32000, "message": "server echoed test-token"},
                }
            )
            return
        if self.tool_error_echo:
            self._respond_json(
                {
                    "jsonrpc": "2.0",
                    "id": request_body.get("id"),
                    "result": {
                        "isError": True,
                        "content": [
                            {"type": "text", "text": "server echoed test-token"}
                        ],
                    },
                }
            )
            return
        if isinstance(params, dict) and params.get("name") == "unknown-tool":
            self._respond_json(
                {
                    "jsonrpc": "2.0",
                    "id": request_body.get("id"),
                    "error": {"code": -32601, "message": "unknown tool"},
                }
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
    _McpHandler.echo_token = False
    _McpHandler.rpc_error_echo = False
    _McpHandler.tool_error_echo = False
    _McpHandler.invalid_initialize = False
    _McpHandler.redirect_once = False
    _McpHandler.slow = False
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
    client = McpHttpClient(
        McpSettings(endpoint_url=endpoint, access_token="test-token", retry_backoff_s=0)
    )

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
        McpSettings(
            endpoint_url=endpoint,
            access_token="test-token",
            max_retries=1,
            retry_backoff_s=0,
        )
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
    client = McpHttpClient(
        McpSettings(endpoint_url=endpoint, access_token="test-token", retry_backoff_s=0)
    )

    # When & then: the successful stub path is intentionally read-only and unknown calls are rejected locally
    with pytest.raises(McpTransportError, match="not allowed"):
        client.call_tool("unknown-tool", {})
    client.close()
    assert _McpHandler.requests == []


def test_http_client_redacts_token_from_json_rpc_error(
    mcp_server: tuple[str, ThreadingHTTPServer],
) -> None:
    # Given: a JSON-RPC error that echoes the bearer token
    endpoint, _server = mcp_server
    _McpHandler.rpc_error_echo = True
    client = McpHttpClient(
        McpSettings(endpoint_url=endpoint, access_token="test-token", retry_backoff_s=0)
    )

    # When & then: the protocol error is safe to persist or display
    with pytest.raises(McpProtocolError) as caught:
        client.call_tool("notion-search", {"query": "PostgreSQL"})
    client.close()

    assert "test-token" not in str(caught.value)


def test_http_client_redacts_token_from_error_tool_result(
    mcp_server: tuple[str, ThreadingHTTPServer],
) -> None:
    # Given: an MCP tool result that reports an error and echoes the bearer token
    endpoint, _server = mcp_server
    _McpHandler.tool_error_echo = True
    client = McpHttpClient(
        McpSettings(endpoint_url=endpoint, access_token="test-token", retry_backoff_s=0)
    )

    # When & then: the returned error result cannot leak the token downstream
    exchange = client.call_tool("notion-search", {"query": "PostgreSQL"})
    client.close()
    assert exchange.result.content[0].text == "server echoed [redacted]"


def test_successful_tool_result_redaction_preserves_long_page_content() -> None:
    # Given: a valid page payload larger than the short error-detail safety limit
    long_content = "page-content " * 100
    result = McpToolResult.model_validate(
        {
            "content": [{"type": "text", "text": long_content}],
            "structuredContent": {"content": long_content},
        }
    )

    # When: credentials are redacted from a successful tool result
    redacted = _redact_tool_result(result, "test-token")

    # Then: page content remains complete for JSON parsing and answer grounding
    assert redacted.content[0].text == long_content
    assert redacted.structured_content == {"content": long_content}


def test_http_client_classifies_an_initialize_response_without_result(
    mcp_server: tuple[str, ThreadingHTTPServer],
) -> None:
    # Given: a malformed initialize response without result or error
    endpoint, _server = mcp_server
    _McpHandler.invalid_initialize = True
    client = McpHttpClient(
        McpSettings(endpoint_url=endpoint, access_token="test-token", retry_backoff_s=0)
    )

    # When & then: malformed protocol data becomes a typed error, not an AttributeError
    with pytest.raises(McpProtocolError, match="no result"):
        client.call_tool("notion-search", {"query": "PostgreSQL"})
    client.close()


def test_http_client_redacts_bearer_token_when_mcp_rejects_authentication(
    mcp_server: tuple[str, ThreadingHTTPServer],
) -> None:
    # Given: an MCP server that rejects the request with a token-shaped detail
    endpoint, _server = mcp_server
    _McpHandler.auth_failure = True
    client = McpHttpClient(
        McpSettings(endpoint_url=endpoint, access_token="test-token", retry_backoff_s=0)
    )

    # When & then: the transport exposes the status without leaking the credential
    with pytest.raises(McpHttpError) as caught:
        client.call_tool("notion-search", {"query": "PostgreSQL"})
    client.close()

    assert caught.value.status_code == 401
    assert "test-token" not in str(caught.value)
    assert "Bearer [redacted]" in str(caught.value)


def test_http_client_redacts_a_raw_echoed_token_from_mcp_error_body(
    mcp_server: tuple[str, ThreadingHTTPServer],
) -> None:
    # Given: an MCP server that accidentally echoes the bearer value without its scheme
    endpoint, _server = mcp_server
    _McpHandler.echo_token = True
    client = McpHttpClient(
        McpSettings(endpoint_url=endpoint, access_token="test-token", retry_backoff_s=0)
    )

    # When & then: the transport removes the exact credential from the exposed detail
    with pytest.raises(McpHttpError) as caught:
        client.call_tool("notion-search", {"query": "PostgreSQL"})
    client.close()

    assert caught.value.status_code == 401
    assert "test-token" not in str(caught.value)
    assert "[redacted]" in str(caught.value)


def test_http_client_does_not_follow_redirects_with_bearer_credentials(
    mcp_server: tuple[str, ThreadingHTTPServer],
) -> None:
    # Given: an MCP endpoint that redirects the first request
    endpoint, _server = mcp_server
    _McpHandler.redirect_once = True
    client = McpHttpClient(
        McpSettings(endpoint_url=endpoint, access_token="test-token", retry_backoff_s=0)
    )

    # When & then: redirects are surfaced without issuing a second authenticated request
    with pytest.raises(McpHttpError) as caught:
        client.call_tool("notion-search", {"query": "PostgreSQL"})
    client.close()

    assert caught.value.status_code == 307
    assert len(_McpHandler.requests) == 1


def test_http_client_surfaces_a_read_timeout_as_transport_error(
    mcp_server: tuple[str, ThreadingHTTPServer],
) -> None:
    # Given: an MCP server slower than the configured read timeout
    endpoint, _server = mcp_server
    _McpHandler.slow = True
    client = McpHttpClient(
        McpSettings(
            endpoint_url=endpoint,
            access_token="test-token",
            read_timeout_s=0.01,
            max_retries=0,
            retry_backoff_s=0,
        )
    )

    # When & then: the timeout is classified without exposing protocol internals
    with pytest.raises(McpTransportError, match="MCP transport error"):
        client.call_tool("notion-search", {"query": "PostgreSQL"})
    client.close()


def test_http_client_caps_a_server_supplied_retry_after(
    mcp_server: tuple[str, ThreadingHTTPServer],
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # Given: a retry response that asks the client to wait for an excessive duration
    endpoint, _server = mcp_server
    client = McpHttpClient(
        McpSettings(
            endpoint_url=endpoint,
            access_token="test-token",
            max_retry_after_s=1.0,
            retry_backoff_s=0,
        )
    )
    sleeps: list[float] = []
    monkeypatch.setattr("mcp_transport.time.sleep", sleeps.append)

    # When: the transport applies the server's Retry-After value
    client._wait(0, "999")
    client.close()

    # Then: the server cannot pause the process beyond the configured cap
    assert sleeps == [1.0]


def test_http_client_rejects_a_non_notion_remote_endpoint() -> None:
    # Given: a remote endpoint that would receive the bearer token
    # When & then: the transport refuses to construct a client for it
    with pytest.raises(McpTransportError, match="hosted Notion endpoint"):
        McpHttpClient(
            McpSettings(
                endpoint_url="https://evil.example/mcp",
                access_token="test-token",
            )
        )


def test_http_client_rejects_an_arbitrary_hosted_notion_path() -> None:
    # Given: a hosted Notion hostname with a path outside Streamable HTTP /mcp
    # When & then: credentials cannot be sent to an unapproved endpoint path
    with pytest.raises(McpTransportError, match="hosted Notion endpoint"):
        McpHttpClient(
            McpSettings(
                endpoint_url="https://mcp.notion.com/other",
                access_token="test-token",
            )
        )


def test_http_client_rejects_an_unknown_tool_before_initializing_a_session(
    mcp_server: tuple[str, ThreadingHTTPServer],
) -> None:
    # Given: a valid local MCP endpoint and a tool outside the read-only contract
    endpoint, _server = mcp_server
    client = McpHttpClient(
        McpSettings(endpoint_url=endpoint, access_token="test-token", retry_backoff_s=0)
    )

    # When & then: the transport rejects it before any authenticated network call
    with pytest.raises(McpTransportError, match="not allowed"):
        client.call_tool("unknown-tool", {})
    client.close()
    assert _McpHandler.requests == []
