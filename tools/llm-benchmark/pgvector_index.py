"""Snapshot chunking and passage indexing for the pgvector benchmark."""

from __future__ import annotations

import time
from pathlib import Path

import typer
from benchmark_core import Chunk, Document, SnapshotError, chunk_text
from nim_client import NimTransportError
from nim_embedding_client import NimEmbeddingClient
from pgvector_store import PgVectorStore
from rich.console import Console


def chunk_documents(documents: tuple[Document, ...], size: int, overlap: int) -> tuple[Chunk, ...]:
    """Split every document into overlapping chunks for passage embedding."""
    if overlap >= size:
        raise typer.BadParameter("chunk_overlap must be smaller than chunk_size")
    chunks: list[Chunk] = []
    for document in documents:
        chunks.extend(
            Chunk(document.path, document.title, content, 0.0)
            for content in chunk_text(document.content, size, overlap)
        )
    if not chunks:
        raise SnapshotError(Path("<documents>"), "no chunks generated")
    return tuple(chunks)


def index_corpus(
    store: PgVectorStore,
    embedding_client: NimEmbeddingClient,
    chunks: tuple[Chunk, ...],
    corpus_key: str,
    console: Console,
) -> None:
    """Embed passages and atomically replace the persisted vector corpus."""
    started = time.perf_counter()
    vectors: list[tuple[float, ...]] = []
    dimension: int | None = None
    batch_size = embedding_client.batch_size
    for start in range(0, len(chunks), batch_size):
        batch = chunks[start : start + batch_size]
        result = embedding_client.embed(tuple(_passage_text(chunk) for chunk in batch), "passage")
        if dimension is None:
            dimension = len(result.vectors[0])
        if any(len(vector) != dimension for vector in result.vectors):
            raise NimTransportError("NIM returned inconsistent embedding dimensions")
        vectors.extend(result.vectors)
        console.print(f"embedding passages {min(start + batch_size, len(chunks))}/{len(chunks)}")
    if dimension is None:
        raise NimTransportError("NIM returned no passage embeddings")
    store.reset_schema(dimension)
    store.replace_chunks(corpus_key, chunks, tuple(vectors))
    elapsed_ms = (time.perf_counter() - started) * 1000
    console.print(f"[green]pgvector indexed:[/green] {store.count(corpus_key)} chunks, {elapsed_ms:.0f} ms")


def _passage_text(chunk: Chunk) -> str:
    return f"title: {chunk.title}\nsource_path: {chunk.path}\n\n{chunk.content}"
