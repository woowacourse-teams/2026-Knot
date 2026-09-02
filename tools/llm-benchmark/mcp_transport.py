"""Streamable HTTP transport for the read-only Notion MCP tools."""

from __future__ import annotations

import json
import socket
import time
from dataclasses import dataclass
from typing import Final

import httpx2
from mcp_errors import McpHttpError, McpProtocolError, McpTransportError
from mcp_models import JsonObject, McpRpcResponse, McpSettings, McpToolResult
from mcp_wire import parse_response, safe_detail

_RETRYABLE_STATUSES: Final[frozenset[int]] = frozenset({408, 425, 429, 500, 502, 503, 504})
_LIMITS: Final[httpx2.Limits] = httpx2.Limits(
    max_connections=50,
    max_keepalive_connections=20,
    keepalive_expiry=30.0,
)
_SOCKET_OPTIONS: Final[list[tuple[int, int, int]]] = [
    (socket.IPPROTO_TCP, socket.TCP_NODELAY, 1),
]


@dataclass(frozen=True, slots=True)
class McpToolExchange:
    """Parsed tool result plus the HTTP work needed to produce it."""

    result: McpToolResult
    http_requests: int
    retry_count: int
    rate_limit_count: int
    elapsed_ms: float


@dataclass(frozen=True, slots=True)
class _HttpExchange:
    """Internal HTTP response accounting for one JSON-RPC message."""

    response: httpx2.Response | None
    payload: McpRpcResponse | None
    http_requests: int
    retry_count: int
    rate_limit_count: int


class McpHttpClient:
    """Synchronous Streamable HTTP MCP client with bounded read retries."""

    def __init__(self, settings: McpSettings) -> None:
        token = settings.access_token.get_secret_value()
        if not token:
            raise McpTransportError("NOTION_MCP_ACCESS_TOKEN is required")
        timeout = httpx2.Timeout(
            connect=settings.connect_timeout_s,
            read=settings.read_timeout_s,
            write=10.0,
            pool=10.0,
        )
        transport = httpx2.HTTPTransport(
            http2=True,
            retries=0,
            limits=_LIMITS,
            socket_options=_SOCKET_OPTIONS,
        )
        self._client = httpx2.Client(
            transport=transport,
            timeout=timeout,
            follow_redirects=False,
            headers={"Authorization": f"Bearer {token}"},
        )
        self._settings = settings
        self._access_token = token
        self._request_id = 0
        self._session_id: str | None = None
        self._initialized = False

    def close(self) -> None:
        """Close the underlying HTTP connection pool."""
        self._client.close()

    def call_tool(self, name: str, arguments: JsonObject) -> McpToolExchange:
        """Initialize the MCP session when needed and call one read-only tool."""
        started = time.perf_counter()
        initialization = self._initialize() if not self._initialized else _empty_exchange()
        self._initialized = True
        exchange = self._request("tools/call", {"name": name, "arguments": arguments}, tool_name=name)
        payload = exchange.payload
        if payload is None or payload.result is None:
            raise McpProtocolError("MCP tool call returned no result")
        if payload.error is not None:
            raise McpProtocolError(payload.error.message, payload.error.code)
        try:
            result = McpToolResult.model_validate(payload.result)
        except ValueError as error:
            raise McpProtocolError("MCP tool result does not match the protocol") from error
        return McpToolExchange(
            result,
            initialization.http_requests + exchange.http_requests,
            initialization.retry_count + exchange.retry_count,
            initialization.rate_limit_count + exchange.rate_limit_count,
            (time.perf_counter() - started) * 1000,
        )

    def _initialize(self) -> _HttpExchange:
        exchange = self._request(
            "initialize",
            {
                "protocolVersion": self._settings.protocol_version,
                "capabilities": {},
                "clientInfo": {"name": "knot-benchmark", "version": "1.0"},
            },
        )
        payload = exchange.payload
        if payload is None or payload.error is not None:
            reason = "initialize returned no result" if payload is None else payload.error.message
            raise McpProtocolError(reason)
        if exchange.response is not None:
            self._session_id = exchange.response.headers.get("Mcp-Session-Id")
        notification = self._request("notifications/initialized", None, expect_response=False)
        return _HttpExchange(
            exchange.response,
            exchange.payload,
            exchange.http_requests + notification.http_requests,
            exchange.retry_count + notification.retry_count,
            exchange.rate_limit_count + notification.rate_limit_count,
        )

    def _request(
        self,
        method: str,
        params: JsonObject | None,
        *,
        tool_name: str | None = None,
        expect_response: bool = True,
    ) -> _HttpExchange:
        payload: JsonObject = {"jsonrpc": "2.0", "method": method}
        if expect_response:
            payload["id"] = self._next_request_id()
        if params is not None:
            payload["params"] = params
        attempt = 0
        requests = 0
        retries = 0
        rate_limits = 0
        while attempt <= self._settings.max_retries:
            requests += 1
            try:
                response = self._client.post(
                    self._settings.endpoint_url,
                    headers=self._headers(method, tool_name),
                    content=json.dumps(payload, ensure_ascii=False),
                )
            except (httpx2.ConnectError, httpx2.TimeoutException, httpx2.NetworkError) as error:
                if attempt >= self._settings.max_retries:
                    raise McpTransportError(str(error)) from error
                retries += 1
                self._wait(attempt, None)
                attempt += 1
                continue
            if response.status_code in _RETRYABLE_STATUSES and attempt < self._settings.max_retries:
                retries += 1
                rate_limits += response.status_code == 429
                self._wait(attempt, response.headers.get("Retry-After"))
                attempt += 1
                continue
            if response.status_code >= 300:
                raise McpHttpError(response.status_code, safe_detail(response.text, (self._access_token,)))
            parsed = None if not expect_response else parse_response(response)
            return _HttpExchange(response, parsed, requests, retries, rate_limits)
        raise McpTransportError("MCP request retry loop ended unexpectedly")

    def _headers(self, method: str, tool_name: str | None) -> dict[str, str]:
        headers = {
            "Accept": "application/json, text/event-stream",
            "Content-Type": "application/json",
            "MCP-Protocol-Version": self._settings.protocol_version,
            "Mcp-Method": method,
        }
        if tool_name is not None:
            headers["Mcp-Name"] = tool_name
        if self._session_id is not None:
            headers["Mcp-Session-Id"] = self._session_id
        return headers

    def _next_request_id(self) -> int:
        self._request_id += 1
        return self._request_id

    def _wait(self, attempt: int, retry_after: str | None) -> None:
        if retry_after is None:
            delay = self._settings.retry_backoff_s * (2**attempt)
        else:
            try:
                delay = max(0.0, float(retry_after))
            except ValueError:
                delay = self._settings.retry_backoff_s * (2**attempt)
        if delay > 0:
            time.sleep(delay)


def _empty_exchange() -> _HttpExchange:
    return _HttpExchange(None, None, 0, 0, 0)
