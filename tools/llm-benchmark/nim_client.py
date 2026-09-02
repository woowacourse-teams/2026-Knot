"""NVIDIA NIM OpenAI-compatible streaming client for benchmark runs."""

from __future__ import annotations

import socket
import time
from dataclasses import dataclass, field
from typing import Final, Literal

import httpx2
from mcp_models import JsonObject, NimFunctionCall, NimToolCall
from pydantic import BaseModel, ConfigDict, Field
from pydantic_settings import BaseSettings, SettingsConfigDict

_LIMITS: Final[httpx2.Limits] = httpx2.Limits(
    max_connections=200,
    max_keepalive_connections=40,
    keepalive_expiry=30.0,
)
_SOCKET_OPTIONS: Final[list[tuple[int, int, int]]] = [
    (socket.IPPROTO_TCP, socket.TCP_NODELAY, 1),
]
_PROVIDER_ERROR_DETAIL: Final[str] = "provider returned an HTTP error"
ReasoningEffort = Literal[
    "none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra"
]


class NimSettings(BaseSettings):
    """NIM settings parsed from the process environment at the boundary."""

    model_config = SettingsConfigDict(env_prefix="NIM_", extra="ignore", frozen=True)

    base_url: str = "https://integrate.api.nvidia.com/v1"
    api_key: str = ""
    model: str = ""
    embedding_model: str = "nvidia/nemotron-3-embed-1b"
    embedding_query_instruction: str = ""
    temperature: float = 0.0
    max_tokens: int = 512
    embedding_timeout_s: float = Field(default=30.0, gt=0)
    embedding_batch_size: int = Field(default=32, ge=1, le=128)
    read_timeout_s: float = Field(default=90.0, gt=0)
    http_retries: int = Field(default=0, ge=0)
    reasoning_effort: ReasoningEffort | None = None
    enable_thinking: bool | None = None


class ChatMessage(BaseModel):
    """One OpenAI-compatible chat message."""

    model_config = ConfigDict(extra="ignore", frozen=True)

    role: Literal["system", "user", "assistant", "tool"]
    content: str = ""
    tool_call_id: str | None = None
    tool_calls: tuple[NimToolCall, ...] | None = None


class ChatRequest(BaseModel):
    """A deterministic streaming request sent to NIM."""

    model_config = ConfigDict(extra="ignore", frozen=True)

    model: str
    messages: tuple[ChatMessage, ...]
    temperature: float
    max_tokens: int
    stream: Literal[True] = True
    reasoning_effort: ReasoningEffort | None = None
    chat_template_kwargs: dict[str, bool] | None = None
    tools: tuple[JsonObject, ...] | None = None


class _ToolCallFunctionDelta(BaseModel):
    model_config = ConfigDict(extra="ignore", frozen=True)

    name: str | None = None
    arguments: str | None = None


class _ToolCallDelta(BaseModel):
    model_config = ConfigDict(extra="ignore", frozen=True)

    index: int = Field(default=0, ge=0)
    id: str | None = None
    type: Literal["function"] | None = None
    function: _ToolCallFunctionDelta | None = None


class _Delta(BaseModel):
    model_config = ConfigDict(extra="ignore", frozen=True)

    content: str | None = None
    tool_calls: tuple[_ToolCallDelta, ...] = ()


class _Choice(BaseModel):
    model_config = ConfigDict(extra="ignore", frozen=True)

    delta: _Delta
    finish_reason: str | None = None


class _StreamChunk(BaseModel):
    model_config = ConfigDict(extra="ignore", frozen=True)

    choices: tuple[_Choice, ...] = Field(default_factory=tuple)


@dataclass(slots=True)
class _ToolCallAccumulator:
    call_id: str = ""
    name: str = ""
    argument_fragments: list[str] = field(default_factory=list)


class NimConfigurationError(Exception):
    """Raised when required NIM settings are absent."""

    __slots__ = ("field",)

    field: str

    def __init__(self, field: str) -> None:
        super().__init__(field)
        self.field = field

    def __str__(self) -> str:
        return f"NIM_{self.field.upper()} is required"


class NimRequestError(Exception):
    """Raised when NIM returns an unsuccessful HTTP response."""

    __slots__ = ("detail", "status_code")

    status_code: int
    detail: str

    def __init__(self, status_code: int, detail: str) -> None:
        super().__init__(status_code)
        self.status_code = status_code
        self.detail = _PROVIDER_ERROR_DETAIL

    def __str__(self) -> str:
        return f"NIM request failed with HTTP {self.status_code}: {self.detail}"


class NimTransportError(Exception):
    """Raised when the NIM endpoint cannot be reached."""

    __slots__ = ("reason",)

    reason: str

    def __init__(self, reason: str) -> None:
        super().__init__(reason)
        self.reason = reason

    def __str__(self) -> str:
        return f"NIM transport error: {self.reason}"


@dataclass(frozen=True, slots=True)
class NimResult:
    """One completed streaming response and its latency measurements."""

    text: str
    ttft_ms: float
    total_ms: float
    tool_calls: tuple[NimToolCall, ...] = ()


class NimClient:
    """Small synchronous client with tuned HTTP/2 and streaming timeouts."""

    def __init__(self, settings: NimSettings) -> None:
        if not settings.api_key:
            raise NimConfigurationError("api_key")
        if not settings.model:
            raise NimConfigurationError("model")
        timeout = httpx2.Timeout(
            connect=10.0, read=settings.read_timeout_s, write=10.0, pool=10.0
        )
        transport = httpx2.HTTPTransport(
            http2=True,
            retries=settings.http_retries,
            limits=_LIMITS,
            socket_options=_SOCKET_OPTIONS,
        )
        self._client = httpx2.Client(
            transport=transport,
            timeout=timeout,
            base_url=settings.base_url.rstrip("/"),
            headers={"Authorization": f"Bearer {settings.api_key}"},
            follow_redirects=True,
            event_hooks={"request": [_mark_request], "response": [_mark_response]},
        )
        self._settings = settings

    def close(self) -> None:
        """Close the underlying HTTP connection pool."""
        self._client.close()

    def generate(
        self,
        messages: tuple[ChatMessage, ...],
        *,
        tools: tuple[JsonObject, ...] = (),
    ) -> NimResult:
        """Stream one answer and measure first visible content and completion."""
        request = ChatRequest(
            model=self._settings.model,
            messages=messages,
            temperature=self._settings.temperature,
            max_tokens=self._settings.max_tokens,
            reasoning_effort=self._settings.reasoning_effort,
            chat_template_kwargs=(
                None
                if self._settings.enable_thinking is None
                else {"enable_thinking": self._settings.enable_thinking}
            ),
            tools=tools or None,
        )
        started = time.perf_counter()
        try:
            response_context = self._client.stream(
                "POST",
                "/chat/completions",
                json=request.model_dump(mode="json", exclude_none=True),
            )
            with response_context as response:
                if response.status_code >= 400:
                    response.read()
                response.raise_for_status()
                fragments: list[str] = []
                first_content_at: float | None = None
                tool_call_accumulators: dict[int, _ToolCallAccumulator] = {}
                for line in response.iter_lines():
                    payload = parse_sse_line(line)
                    if payload is None:
                        continue
                    chunk = _StreamChunk.model_validate_json(payload)
                    fragment = _fragment(chunk)
                    has_tool_call = _accumulate_tool_calls(
                        chunk, tool_call_accumulators
                    )
                    if not fragment and not has_tool_call:
                        continue
                    if first_content_at is None:
                        first_content_at = time.perf_counter()
                    if fragment:
                        fragments.append(fragment)
        except httpx2.HTTPStatusError as error:
            response = error.response
            response.read()
            raise NimRequestError(response.status_code, response.text[:500]) from error
        except (httpx2.ConnectError, httpx2.TimeoutException) as error:
            raise NimTransportError(str(error)) from error
        finished = time.perf_counter()
        if first_content_at is None:
            raise NimTransportError("NIM returned no answer content or tool call")
        return NimResult(
            "".join(fragments),
            (first_content_at - started) * 1000,
            (finished - started) * 1000,
            _materialize_tool_calls(tool_call_accumulators),
        )


def parse_sse_line(line: str) -> str | None:
    """Return a non-terminal Server-Sent Events data payload."""
    if not line.startswith("data:"):
        return None
    payload = line.removeprefix("data:").strip()
    return None if not payload or payload == "[DONE]" else payload


def _fragment(chunk: _StreamChunk) -> str:
    return "".join(choice.delta.content or "" for choice in chunk.choices)


def _accumulate_tool_calls(
    chunk: _StreamChunk,
    accumulators: dict[int, _ToolCallAccumulator],
) -> bool:
    found = False
    for choice in chunk.choices:
        for delta in choice.delta.tool_calls:
            found = True
            accumulator = accumulators.setdefault(
                delta.index,
                _ToolCallAccumulator(),
            )
            if delta.id:
                if accumulator.call_id and accumulator.call_id != delta.id:
                    raise NimTransportError(
                        f"NIM tool call {delta.index} has conflicting IDs"
                    )
                accumulator.call_id = delta.id
            if delta.function is not None:
                if delta.function.name:
                    accumulator.name += delta.function.name
                if delta.function.arguments:
                    accumulator.argument_fragments.append(delta.function.arguments)
    return found


def _materialize_tool_calls(
    accumulators: dict[int, _ToolCallAccumulator],
) -> tuple[NimToolCall, ...]:
    calls: list[NimToolCall] = []
    for index, accumulator in sorted(accumulators.items()):
        if not accumulator.name:
            raise NimTransportError(f"NIM tool call {index} has no function name")
        try:
            calls.append(
                NimToolCall(
                    id=accumulator.call_id or f"call-{index}",
                    function=NimFunctionCall(
                        name=accumulator.name,
                        arguments="".join(accumulator.argument_fragments) or "{}",
                    ),
                )
            )
        except ValueError as error:
            raise NimTransportError("NIM returned an invalid tool call") from error
    return tuple(calls)


def _mark_request(request: httpx2.Request) -> None:
    request.extensions["benchmark_started_at"] = time.perf_counter()


def _mark_response(response: httpx2.Response) -> None:
    response.extensions["benchmark_response_seen_at"] = time.perf_counter()
