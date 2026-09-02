# /// script
# requires-python = ">=3.14"
# dependencies = ["httpx2[http2,brotli,zstd]", "pydantic", "pydantic-settings", "pytest"]
# ///

# ─── How to run ───
# 1. Install uv (if not installed):
#      curl -LsSf https://astral.sh/uv/install.sh | sh
# 2. Run directly (no venv, no pip install needed):
#      uv run --with httpx2 --with pydantic --with pydantic-settings --with pytest pytest tools/llm-benchmark/test_nim_client.py
# ──────────────────

from __future__ import annotations

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread
from time import sleep

import json

import pytest
from nim_client import (
    ChatMessage,
    NimClient,
    NimRequestError,
    NimSettings,
    NimTransportError,
    parse_sse_line,
)


def test_parse_sse_line_returns_json_payload_only() -> None:
    assert parse_sse_line("event: message") is None
    assert parse_sse_line("data:") is None
    assert parse_sse_line("data: [DONE]") is None
    assert parse_sse_line('data: {"choices": []}') == '{"choices": []}'


class _ForbiddenHandler(BaseHTTPRequestHandler):
    def do_POST(self) -> None:
        body = b'{"error":"forbidden"}'
        self.send_response(403)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args: str) -> None:
        return


class _SlowBodyHandler(_ForbiddenHandler):
    def do_POST(self) -> None:
        body = b'data: {"choices": []}\n\n'
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        sleep(0.2)
        try:
            self.wfile.write(body)
        except BrokenPipeError:
            return


class _ToolCallHandler(BaseHTTPRequestHandler):
    request_body: dict[str, object] | None = None

    def do_POST(self) -> None:
        length = int(self.headers["Content-Length"])
        type(self).request_body = json.loads(self.rfile.read(length))
        body = (
            b'data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call-1","type":"function","function":{"name":"notion-search","arguments":"{\\"query\\":"}}]}}]}\n\n'
            b'data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\\"PostgreSQL\\"}"}}]}}]}\n\n'
            b"data: [DONE]\n\n"
        )
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args: str) -> None:
        return


def test_streaming_http_error_preserves_provider_status_and_detail() -> None:
    server = ThreadingHTTPServer(("127.0.0.1", 0), _ForbiddenHandler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    client = NimClient(
        NimSettings(
            base_url=f"http://127.0.0.1:{server.server_port}/v1",
            api_key="test-key",
            model="test-model",
        )
    )
    try:
        with pytest.raises(NimRequestError) as raised:
            client.generate((ChatMessage(role="user", content="ping"),))
    finally:
        client.close()
        server.shutdown()
        server.server_close()
        thread.join(timeout=1)

    assert raised.value.status_code == 403
    assert raised.value.detail == "provider returned an HTTP error"
    assert "forbidden" not in str(raised.value)


def test_streaming_read_timeout_turns_slow_provider_into_transport_error() -> None:
    server = ThreadingHTTPServer(("127.0.0.1", 0), _SlowBodyHandler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    client = NimClient(
        NimSettings(
            base_url=f"http://127.0.0.1:{server.server_port}/v1",
            api_key="test-key",
            model="test-model",
            read_timeout_s=0.05,
        )
    )
    try:
        with pytest.raises(NimTransportError, match="timed out"):
            client.generate((ChatMessage(role="user", content="ping"),))
    finally:
        client.close()
        server.shutdown()
        server.server_close()
        thread.join(timeout=1)


def test_streaming_tool_call_fragments_are_reassembled_for_mcp_execution() -> None:
    # Given: an OpenAI-compatible stream that sends one MCP call over two deltas
    server = ThreadingHTTPServer(("127.0.0.1", 0), _ToolCallHandler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    client = NimClient(
        NimSettings(
            base_url=f"http://127.0.0.1:{server.server_port}/v1",
            api_key="test-key",
            model="test-model",
        )
    )

    # When: the client parses a tool-call streaming response
    try:
        result = client.generate(
            (ChatMessage(role="user", content="search"),),
            tools=(
                {
                    "type": "function",
                    "function": {"name": "notion-search"},
                },
            ),
        )
    finally:
        client.close()
        server.shutdown()
        server.server_close()
        thread.join(timeout=1)

    # Then: the complete typed call and the tool schema are available to the loop
    assert result.text == ""
    assert len(result.tool_calls) == 1
    assert result.tool_calls[0].id == "call-1"
    assert result.tool_calls[0].function.name == "notion-search"
    assert result.tool_calls[0].function.arguments == '{"query":"PostgreSQL"}'
    assert _ToolCallHandler.request_body is not None
    assert _ToolCallHandler.request_body["tools"] == [
        {"type": "function", "function": {"name": "notion-search"}}
    ]
