"""Comparable context builders for raw, SQL, vector, and MCP-replay access."""

from __future__ import annotations

import time
from dataclasses import dataclass
from pathlib import Path
from typing import Literal, assert_never

from benchmark_core import Chunk, ContextPack, Document, Strategy, build_context
from nim_embedding_client import NimEmbeddingClient
from pgvector_rag import retrieve_pgvector_context
from pgvector_store import PgVectorStore, StoredChunk
from retrieval_policy import QueryPlan, plan_query

AccessLabel = Literal["raw", "rag", "db", "mcp-replay"]


@dataclass(frozen=True, slots=True)
class AccessContextTiming:
    """Context pack and component timings for one access strategy."""

    context: ContextPack
    embedding_ms: float
    database_ms: float


def build_access_context(
    label: AccessLabel,
    question: str,
    documents: tuple[Document, ...],
    store: PgVectorStore,
    embedding_client: NimEmbeddingClient,
    corpus_key: str,
    top_k: int,
    *,
    query_plan: QueryPlan | None = None,
    excluded_source_paths: frozenset[str] = frozenset(),
) -> AccessContextTiming:
    """Build one strategy's context while separating embedding and database time."""
    effective_plan = query_plan or plan_query(question, ())
    retrieval_query = effective_plan.search_query
    match label:
        case "raw":
            started = time.perf_counter()
            context = build_context(Strategy.RAW, documents, retrieval_query, top_k)
            return AccessContextTiming(context, 0.0, (time.perf_counter() - started) * 1000)
        case "mcp-replay":
            started = time.perf_counter()
            context = build_context(Strategy.MCP_REPLAY, documents, retrieval_query, top_k)
            return AccessContextTiming(context, 0.0, (time.perf_counter() - started) * 1000)
        case "db":
            started = time.perf_counter()
            rows = store.search_keyword(corpus_key, retrieval_query, top_k)
            context = _stored_context(rows, 0)
            return AccessContextTiming(context, 0.0, (time.perf_counter() - started) * 1000)
        case "rag":
            timing = retrieve_pgvector_context(
                retrieval_query,
                store,
                embedding_client,
                corpus_key,
                top_k,
                query_kind=effective_plan.kind,
                excluded_source_paths=excluded_source_paths,
                related_documents=effective_plan.related_documents,
            )
            return AccessContextTiming(timing.context, timing.embedding_ms, timing.vector_db_ms)
        case unreachable:
            assert_never(unreachable)


def _stored_context(rows: tuple[StoredChunk, ...], tool_calls: int) -> ContextPack:
    chunks = tuple(Chunk(Path(row.source_path), row.title, row.content, row.score) for row in rows)
    return ContextPack(
        "\n\n".join(_render_chunk(chunk) for chunk in chunks),
        tuple(row.source_path for row in rows),
        len(rows),
        tool_calls,
    )


def _render_chunk(chunk: Chunk) -> str:
    return f"## {chunk.title}\nsource_path: {chunk.path}\n\n{chunk.content}"
