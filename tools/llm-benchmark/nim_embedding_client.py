"""NVIDIA NIM embedding client used by the pgvector benchmark."""

from __future__ import annotations

import socket
import time
from dataclasses import dataclass
from typing import Final, Literal

import httpx2
from nim_client import (
    NimConfigurationError,
    NimRequestError,
    NimSettings,
    NimTransportError,
)
from pydantic import BaseModel, ConfigDict, Field, ValidationError

_LIMITS: Final[httpx2.Limits] = httpx2.Limits(
    max_connections=200,
    max_keepalive_connections=40,
    keepalive_expiry=30.0,
)
_SOCKET_OPTIONS: Final[list[tuple[int, int, int]]] = [(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)]


class _EmbeddingItem(BaseModel):
    model_config = ConfigDict(extra="ignore", frozen=True)

    index: int
    embedding: tuple[float, ...]


class _EmbeddingResponse(BaseModel):
    model_config = ConfigDict(extra="ignore", frozen=True)

    data: tuple[_EmbeddingItem, ...] = Field(default_factory=tuple)


class NimEmbeddingInputError(Exception):
    """Raised when an embedding request exceeds the configured batch size."""

    __slots__ = ("batch_size", "limit")

    batch_size: int
    limit: int

    def __init__(self, batch_size: int, limit: int) -> None:
        super().__init__(batch_size, limit)
        self.batch_size = batch_size
        self.limit = limit

    def __str__(self) -> str:
        return f"embedding batch size {self.batch_size} exceeds configured limit {self.limit}"


@dataclass(frozen=True, slots=True)
class EmbeddingBatch:
    """Embedding vectors and the elapsed provider time for one batch."""

    vectors: tuple[tuple[float, ...], ...]
    elapsed_ms: float


class NimEmbeddingClient:
    """Synchronous OpenAI-compatible embedding client."""

    def __init__(self, settings: NimSettings) -> None:
        if not settings.api_key:
            raise NimConfigurationError("api_key")
        if not settings.embedding_model:
            raise NimConfigurationError("embedding_model")
        timeout = httpx2.Timeout(connect=10.0, read=settings.embedding_timeout_s, write=10.0, pool=10.0)
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
        )
        self._settings = settings

    def close(self) -> None:
        """Close the underlying connection pool."""
        self._client.close()

    @property
    def batch_size(self) -> int:
        """Return the configured maximum number of texts per request."""
        return self._settings.embedding_batch_size

    def embed(
        self,
        texts: tuple[str, ...],
        input_type: Literal["query", "passage"],
    ) -> EmbeddingBatch:
        """Embed a bounded batch of query or passage texts."""
        if not texts:
            return EmbeddingBatch((), 0.0)
        if len(texts) > self._settings.embedding_batch_size:
            raise NimEmbeddingInputError(len(texts), self._settings.embedding_batch_size)
        started = time.perf_counter()
        try:
            response = self._client.post(
                "/embeddings",
                json={
                    "input": [self._format_input(text, input_type) for text in texts],
                    "model": self._settings.embedding_model,
                    "input_type": input_type,
                    "encoding_format": "float",
                },
            )
            response.raise_for_status()
            parsed = _EmbeddingResponse.model_validate_json(response.text)
        except httpx2.HTTPStatusError as error:
            response = error.response
            response.read()
            raise NimRequestError(response.status_code, response.text[:500]) from error
        except (httpx2.ConnectError, httpx2.TimeoutException) as error:
            raise NimTransportError(str(error)) from error
        except ValidationError as error:
            raise NimTransportError("NIM returned an invalid embedding response") from error
        elapsed_ms = (time.perf_counter() - started) * 1000
        ordered = tuple(item.embedding for item in sorted(parsed.data, key=lambda item: item.index))
        if len(ordered) != len(texts):
            raise NimTransportError("NIM returned an incomplete embedding response")
        return EmbeddingBatch(ordered, elapsed_ms)

    def _format_input(self, text: str, input_type: Literal["query", "passage"]) -> str:
        if input_type != "query" or not self._settings.embedding_query_instruction:
            return text
        return f"Instruct: {self._settings.embedding_query_instruction}\nQuery: {text}"
