"""pgvector-backed retrieval primitives for the LLM benchmark."""

from __future__ import annotations

import time
from dataclasses import dataclass
from pathlib import Path
from typing import TYPE_CHECKING

from benchmark_core import Chunk, ContextPack, tokenize
from pgvector_store import PgVectorStore, StoredChunk
from retrieval_policy import QueryKind, classify_query
from retrieval_ranking import (
    _content_evidence,
    _missing_identifier_evidence,
    rank_hybrid_candidates,
)

if TYPE_CHECKING:
    from nim_embedding_client import NimEmbeddingClient


@dataclass(frozen=True, slots=True)
class RetrievalTiming:
    """Retrieved context plus query-embedding and database timings."""

    context: ContextPack
    embedding_ms: float
    vector_db_ms: float


def retrieve_pgvector_context(
    query: str,
    store: PgVectorStore,
    embedding_client: NimEmbeddingClient,
    corpus_key: str,
    top_k: int,
    *,
    query_kind: QueryKind | None = None,
    excluded_source_paths: frozenset[str] = frozenset(),
    related_documents: bool = False,
) -> RetrievalTiming:
    """Embed a query, union lexical/vector candidates, and build context."""
    embedding_batch = embedding_client.embed((query,), "query")
    vector = embedding_batch.vectors[0]
    database_started = time.perf_counter()
    candidate_limit = max(top_k * 10, 50)
    vector_passages = store.search(corpus_key, vector, candidate_limit, distinct_sources=False)
    keyword_passages = store.search_keyword(corpus_key, query, candidate_limit, distinct_sources=False)
    vector_candidates = _distinct_source_passages(vector_passages, candidate_limit)
    keyword_candidates = _distinct_source_passages(keyword_passages, candidate_limit)
    stored = rank_hybrid_candidates(
        query,
        vector_candidates,
        keyword_candidates,
        query_kind or classify_query(query),
        top_k,
        excluded_source_paths,
        related_documents,
    )
    if _missing_identifier_evidence(query, keyword_candidates):
        stored = ()
    stored = _merge_context_passages(query, stored, vector_passages, keyword_passages, query_kind or classify_query(query))
    database_ms = (time.perf_counter() - database_started) * 1000
    context = _context_pack(stored)
    return RetrievalTiming(context, embedding_batch.elapsed_ms, database_ms)


def _context_pack(stored: tuple[StoredChunk, ...]) -> ContextPack:
    chunks = tuple(Chunk(Path(item.source_path), item.title, item.content, item.score) for item in stored)
    return ContextPack(
        "\n\n".join(_render_chunk(chunk) for chunk in chunks),
        tuple(item.source_path for item in stored),
        len(stored),
        0,
    )


def _distinct_source_passages(
    passages: tuple[StoredChunk, ...],
    limit: int,
) -> tuple[StoredChunk, ...]:
    """Collapse ranked passages to one source-level candidate per document."""
    selected: list[StoredChunk] = []
    seen: set[str] = set()
    for passage in passages:
        if passage.source_path in seen:
            continue
        seen.add(passage.source_path)
        selected.append(passage)
        if len(selected) == limit:
            break
    return tuple(selected)


def _merge_context_passages(
    query: str,
    selected: tuple[StoredChunk, ...],
    vector_passages: tuple[StoredChunk, ...],
    keyword_passages: tuple[StoredChunk, ...],
    query_kind: QueryKind,
) -> tuple[StoredChunk, ...]:
    """Attach the strongest evidence passages to each selected source document."""
    passage_limit = _passage_limit(query, query_kind)
    result: list[StoredChunk] = []
    for item in selected:
        passages = [item]
        passages.extend(passage for passage in vector_passages if passage.source_path == item.source_path)
        passages.extend(passage for passage in keyword_passages if passage.source_path == item.source_path)
        unique = {passage.content: passage for passage in passages}
        ranked = sorted(
            unique.values(),
            key=lambda passage: (-_content_evidence(query, passage, query_kind), passage.content),
        )
        content = "\n\n--- related passage ---\n\n".join(passage.content for passage in ranked[:passage_limit])
        result.append(StoredChunk(item.source_path, item.title, content, item.score))
    return tuple(result)


def _passage_limit(query: str, query_kind: QueryKind) -> int:
    if "폴더" in set(tokenize(query)) or {"shared", "feature"} & set(tokenize(query)):
        return 4
    return 3 if query_kind is QueryKind.DECISION_REASON else 2


def _hybrid_select(query: str, candidates: tuple[StoredChunk, ...], top_k: int) -> tuple[StoredChunk, ...]:
    """Keep the old private helper compatible with deterministic tests."""
    return rank_hybrid_candidates(query, candidates, (), classify_query(query), top_k)


def _render_chunk(chunk: Chunk) -> str:
    return f"## {chunk.title}\nsource_path: {chunk.path}\n\n{chunk.content}"
