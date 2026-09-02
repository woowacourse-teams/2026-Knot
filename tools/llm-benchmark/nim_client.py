"""NVIDIA NIM OpenAI-compatible streaming client for benchmark runs."""

from __future__ import annotations

import socket
import time
from dataclasses import dataclass
from typing import Final, Literal

import httpx2
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
ReasoningEffort = Literal["none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra"]


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

    role: Literal["system", "user", "assistant"]
    content: str


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


class _Delta(BaseModel):
    model_config = ConfigDict(extra="ignore", frozen=True)

    content: str | None = None


class _Choice(BaseModel):
    model_config = ConfigDict(extra="ignore", frozen=True)

    delta: _Delta


class _StreamChunk(BaseModel):
    model_config = ConfigDict(extra="ignore", frozen=True)

    choices: tuple[_Choice, ...] = Field(default_factory=tuple)


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


class NimClient:
    """Small synchronous client with tuned HTTP/2 and streaming timeouts."""

    def __init__(self, settings: NimSettings) -> None:
        if not settings.api_key:
            raise NimConfigurationError("api_key")
        if not settings.model:
            raise NimConfigurationError("model")
        timeout = httpx2.Timeout(connect=10.0, read=settings.read_timeout_s, write=10.0, pool=10.0)
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

    def generate(self, messages: tuple[ChatMessage, ...]) -> NimResult:
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
                for line in response.iter_lines():
                    payload = parse_sse_line(line)
                    if payload is None:
                        continue
                    chunk = _StreamChunk.model_validate_json(payload)
                    fragment = _fragment(chunk)
                    if not fragment:
                        continue
                    if first_content_at is None:
                        first_content_at = time.perf_counter()
                    fragments.append(fragment)
        except httpx2.HTTPStatusError as error:
            response = error.response
            response.read()
            raise NimRequestError(response.status_code, response.text[:500]) from error
        except (httpx2.ConnectError, httpx2.TimeoutException) as error:
            raise NimTransportError(str(error)) from error
        finished = time.perf_counter()
        if first_content_at is None:
            raise NimTransportError("NIM returned no visible answer content")
        return NimResult(
            "".join(fragments),
            (first_content_at - started) * 1000,
            (finished - started) * 1000,
        )


def parse_sse_line(line: str) -> str | None:
    """Return a non-terminal Server-Sent Events data payload."""
    if not line.startswith("data:"):
        return None
    payload = line.removeprefix("data:").strip()
    return None if not payload or payload == "[DONE]" else payload


def _fragment(chunk: _StreamChunk) -> str:
    return "".join(choice.delta.content or "" for choice in chunk.choices)


def _mark_request(request: httpx2.Request) -> None:
    request.extensions["benchmark_started_at"] = time.perf_counter()


def _mark_response(response: httpx2.Response) -> None:
    response.extensions["benchmark_response_seen_at"] = time.perf_counter()
