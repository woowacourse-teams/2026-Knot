"""pgvector-backed retrieval primitives for the LLM benchmark."""

from __future__ import annotations

import time
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

from benchmark_core import Chunk, ContextPack, tokenize
from nim_embedding_client import NimEmbeddingClient
from pgvector_store import PgVectorStore, StoredChunk


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
) -> RetrievalTiming:
    """Embed a query, search pgvector, and build the LLM context pack."""
    embedding_batch = embedding_client.embed((query,), "query")
    vector = embedding_batch.vectors[0]
    database_started = time.perf_counter()
    candidates = store.search(corpus_key, vector, max(top_k * 50, top_k))
    stored = _hybrid_select(query, candidates, top_k)
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


def _hybrid_select(query: str, candidates: tuple[StoredChunk, ...], top_k: int) -> tuple[StoredChunk, ...]:
    query_terms = Counter(tokenize(query))
    lexical_scores = tuple(
        sum(min(count, Counter(tokenize(f"{item.title} {item.source_path} {item.content}"))[token]) for token, count in query_terms.items())
        for item in candidates
    )
    lexical_order = sorted(range(len(candidates)), key=lambda index: (-lexical_scores[index], index))
    lexical_ranks = {index: rank + 1 for rank, index in enumerate(lexical_order)}
    ranked = sorted(
        enumerate(candidates),
        key=lambda pair: -(1 / (60 + pair[0] + 1) + 1 / (60 + lexical_ranks[pair[0]])),
    )
    return tuple(item for _, item in ranked[:top_k])


def _render_chunk(chunk: Chunk) -> str:
    return f"## {chunk.title}\nsource_path: {chunk.path}\n\n{chunk.content}"
