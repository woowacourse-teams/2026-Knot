"""Typed snapshot, gold-set, and retrieval primitives for the LLM benchmark."""

from __future__ import annotations

import math
import re
from collections import Counter
from collections.abc import Mapping
from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path
from types import MappingProxyType
from typing import Final, assert_never

_SUPPORTED_SUFFIXES: Final[frozenset[str]] = frozenset({".csv", ".markdown", ".md", ".txt"})
_SENSITIVE_PATH_TERMS: Final[frozenset[str]] = frozenset(
    {
        "credential",
        "credentials",
        "key",
        "keys",
        "password",
        "passwords",
        "pem",
        "private",
        "secret",
        "secrets",
        "token",
        "tokens",
        "비밀번호",
    }
)
_PRIVATE_KEY_PATTERN: Final[re.Pattern[str]] = re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----")
_CREDENTIAL_ASSIGNMENT_PATTERN: Final[re.Pattern[str]] = re.compile(
    r"(?im)^\s*(?:api[_ -]?key|client[_ -]?secret|password|access[_ -]?token|refresh[_ -]?token|private[_ -]?key)\s*[:=]\s*\S+"
)
_TOKEN_PATTERN: Final[re.Pattern[str]] = re.compile(r"[0-9A-Za-z가-힣_]+")
_TOKEN_ALIASES: Final[Mapping[str, str]] = MappingProxyType(
    {
        "db": "database",
        "데이터베이스": "database",
        "노션": "notion",
    }
)


class Strategy(StrEnum):
    """A document delivery strategy used by the benchmark."""

    RAW = "raw"
    RAG = "rag"
    MCP_REPLAY = "mcp-replay"


class SnapshotError(Exception):
    """Raised when a local export cannot be loaded as a snapshot."""

    __slots__ = ("path", "reason")

    path: Path
    reason: str

    def __init__(self, path: Path, reason: str) -> None:
        super().__init__(reason)
        self.path = path
        self.reason = reason

    def __str__(self) -> str:
        return f"snapshot error at {self.path}: {self.reason}"


@dataclass(frozen=True, slots=True)
class Document:
    """A normalized document loaded from a local Notion export."""

    path: Path
    title: str
    content: str


@dataclass(frozen=True, slots=True)
class Chunk:
    """A retrieval unit retaining its source document identity."""

    path: Path
    title: str
    content: str
    score: float


@dataclass(frozen=True, slots=True)
class ContextPack:
    """The context and retrieval metadata sent to the answer generator."""

    text: str
    source_paths: tuple[str, ...]
    retrieved_count: int
    tool_calls: int


def load_snapshot(root: Path) -> tuple[Document, ...]:
    """Load supported text files below a snapshot root in stable path order."""
    if not root.is_dir():
        raise SnapshotError(root, "directory does not exist")

    documents: list[Document] = []
    for path in sorted(root.rglob("*")):
        relative_path = path.relative_to(root)
        if not path.is_file() or any(part.startswith(".") for part in relative_path.parts):
            continue
        if path.suffix.lower() not in _SUPPORTED_SUFFIXES:
            continue
        if _is_sensitive_path(relative_path) or _is_duplicate_database_export(path):
            continue
        try:
            content = path.read_text(encoding="utf-8").strip()
        except UnicodeDecodeError as error:
            raise SnapshotError(path, "file is not valid UTF-8") from error
        if not content or _is_sensitive_content(content):
            continue
        documents.append(Document(relative_path, _title_for(path, content), content))

    if not documents:
        raise SnapshotError(root, "no supported non-empty documents found")
    return tuple(documents)


def build_context(
    strategy: Strategy,
    documents: tuple[Document, ...],
    query: str,
    top_k: int,
) -> ContextPack:
    """Build a context pack for one strategy and query."""
    match strategy:
        case Strategy.RAW:
            selected = tuple(
                Chunk(document.path, document.title, document.content, 0.0)
                for document in documents
            )
            tool_calls = 0
        case Strategy.RAG | Strategy.MCP_REPLAY:
            selected = retrieve(documents, query, top_k)
            tool_calls = 1 if strategy is Strategy.MCP_REPLAY else 0
        case unreachable:
            assert_never(unreachable)
    return ContextPack(
        "\n\n".join(_render_chunk(chunk) for chunk in selected),
        tuple(str(chunk.path) for chunk in selected),
        len(selected),
        tool_calls,
    )


def retrieve(documents: tuple[Document, ...], query: str, top_k: int) -> tuple[Chunk, ...]:
    """Retrieve top lexical chunks as a deterministic RAG baseline."""
    if top_k < 1:
        return ()
    query_terms = Counter(tokenize(query))
    if not query_terms:
        return ()
    document_frequency = Counter(
        token
        for document in documents
        for token in set(tokenize(document.title)) | set(tokenize(document.content))
    )
    term_weights = {
        token: math.log((1.0 + len(documents)) / (1.0 + document_frequency[token])) + 1.0
        for token in query_terms
    }
    best_by_document: dict[Path, Chunk] = {}
    for document in documents:
        title_terms = set(tokenize(document.title))
        for content in chunk_text(document.content):
            content_terms = Counter(tokenize(content))
            overlap = sum(
                min(count, content_terms[token]) * term_weights[token]
                for token, count in query_terms.items()
            )
            title_overlap = sum(term_weights[token] for token in query_terms if token in title_terms)
            score = overlap + (0.5 * title_overlap)
            if score > 0:
                candidate = Chunk(document.path, document.title, content, score)
                current = best_by_document.get(document.path)
                if current is None or candidate.score > current.score:
                    best_by_document[document.path] = candidate
    return tuple(
        sorted(best_by_document.values(), key=lambda chunk: (-chunk.score, str(chunk.path)))[:top_k]
    )


def _title_for(path: Path, content: str) -> str:
    heading = re.search(r"(?m)^#\s+(.+?)\s*$", content)
    return heading.group(1).strip() if heading else path.stem


def _is_sensitive_path(path: Path) -> bool:
    return any(
        token == sensitive_term or token.startswith(sensitive_term) or sensitive_term in token.split("_")
        for part in path.parts
        for token in _TOKEN_PATTERN.findall(part.casefold())
        for sensitive_term in _SENSITIVE_PATH_TERMS
    )


def _is_sensitive_content(content: str) -> bool:
    return bool(_PRIVATE_KEY_PATTERN.search(content) or _CREDENTIAL_ASSIGNMENT_PATTERN.search(content))


def _is_duplicate_database_export(path: Path) -> bool:
    name = path.name
    if not name.casefold().endswith("_all.csv"):
        return False
    regular_name = f"{name[:-len('_all.csv')]}.csv"
    return path.with_name(regular_name).exists()


def tokenize(text: str) -> tuple[str, ...]:
    return tuple(_TOKEN_ALIASES.get(token, token) for token in _TOKEN_PATTERN.findall(text.lower()))


def chunk_text(content: str, size: int = 1800, overlap: int = 200) -> tuple[str, ...]:
    """Split document content into overlapping retrieval units."""
    chunks: list[str] = []
    start = 0
    while start < len(content):
        end = min(len(content), start + size)
        chunk = content[start:end].strip()
        if chunk:
            chunks.append(chunk)
        if end == len(content):
            break
        start = end - overlap
    return tuple(chunks)


def _render_chunk(chunk: Chunk) -> str:
    return f"## {chunk.title}\nsource_path: {chunk.path}\n\n{chunk.content}"
