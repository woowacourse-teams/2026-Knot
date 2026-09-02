"""Source-level hybrid ranking and passage relevance helpers."""

from __future__ import annotations

import re
from dataclasses import dataclass

from benchmark_core import tokenize
from pgvector_store import StoredChunk
from retrieval_policy import QueryKind, authority_score


@dataclass(frozen=True, slots=True)
class CandidateEvidence:
    """One source document and its ranks in the two retrieval channels."""

    item: StoredChunk
    vector_rank: int | None
    keyword_rank: int | None
    vector_item: StoredChunk | None = None
    keyword_item: StoredChunk | None = None


def rank_hybrid_candidates(
    query: str,
    vector_candidates: tuple[StoredChunk, ...],
    keyword_candidates: tuple[StoredChunk, ...],
    query_kind: QueryKind,
    top_k: int,
    excluded_source_paths: frozenset[str] = frozenset(),
    related_documents: bool = False,
) -> tuple[StoredChunk, ...]:
    """Merge, de-duplicate, and deterministically rank both candidate lists."""
    if top_k < 1:
        return ()
    merged: dict[str, CandidateEvidence] = {}
    for rank, item in enumerate(vector_candidates, start=1):
        if item.source_path in excluded_source_paths:
            continue
        merged[item.source_path] = CandidateEvidence(item, rank, None, item, None)
    for rank, item in enumerate(keyword_candidates, start=1):
        if item.source_path in excluded_source_paths:
            continue
        current = merged.get(item.source_path)
        if current is None:
            merged[item.source_path] = CandidateEvidence(item, None, rank, None, item)
            continue
        vector_item = current.vector_item or current.item
        merged[item.source_path] = CandidateEvidence(
            _preferred_item(query, vector_item, item, query_kind),
            current.vector_rank,
            rank,
            vector_item,
            item,
        )

    candidates = tuple(merged.values())
    conflict_candidates = tuple(candidate for candidate in candidates if _has_conflict_evidence(query, candidate.item))
    if query_kind is QueryKind.CONFLICT and conflict_candidates:
        candidates = conflict_candidates
    ranked = sorted(
        ((candidate, _candidate_score(query, candidate, query_kind)) for candidate in candidates),
        key=lambda pair: (-pair[1], pair[0].item.source_path),
    )
    if not ranked:
        return ()
    if related_documents:
        return tuple(pair[0].item for pair in ranked[:top_k])
    maximum = ranked[0][1]
    selected = [pair for pair in ranked if pair[1] >= maximum - _score_margin(query_kind)][:top_k]
    if query_kind is QueryKind.MEETING_DATE and selected:
        title = selected[0][0].item.title.casefold().strip()
        duplicates = [pair for pair in ranked if pair[0].item.title.casefold().strip() == title]
        if len(duplicates) > 1:
            selected = duplicates[:top_k]
    return tuple(pair[0].item for pair in selected)


def _candidate_score(query: str, candidate: CandidateEvidence, query_kind: QueryKind) -> float:
    vector_signal = _rank_signal(candidate.vector_rank)
    keyword_signal = _rank_signal(candidate.keyword_rank)
    authority = authority_score(candidate.item.source_path, candidate.item.title, query_kind, query)
    overlap = _token_overlap(query, candidate.item)
    identifier_signal = _identifier_overlap(query, candidate.item)
    score = (0.38 * vector_signal) + (0.18 * keyword_signal) + (0.14 * authority) + (0.2 * overlap)
    score += _title_phrase_overlap(query, candidate.item)
    if identifier_signal is not None:
        score += 0.35 * identifier_signal
        if identifier_signal == 0.0:
            score -= 0.15
    if authority <= 0.2:
        score -= 0.4
    if query_kind in (QueryKind.FACT, QueryKind.AMBIGUOUS, QueryKind.DECISION_REASON) and authority >= 0.9:
        score += 0.3 if query_kind is QueryKind.FACT else 0.35
    if query_kind is QueryKind.MEETING_DATE and authority >= 0.9:
        score += 0.25
    return score


def _preferred_item(query: str, vector_item: StoredChunk, keyword_item: StoredChunk, query_kind: QueryKind) -> StoredChunk:
    return keyword_item if _content_evidence(query, keyword_item, query_kind) >= _content_evidence(query, vector_item, query_kind) else vector_item


def _content_evidence(query: str, item: StoredChunk, query_kind: QueryKind) -> float:
    terms = set(tokenize(query))
    content_terms = set(tokenize(item.content))
    overlap = len(terms & content_terms) / max(len(terms), 1)
    identifier = _identifier_overlap(query, item) or 0.0
    content = item.content.casefold()
    reason = sum(marker in content for marker in ("이유", "근거", "때문", "필요", "대안", "고려", "확정", "미결"))
    reason_bonus = 0.08 * reason if query_kind is QueryKind.DECISION_REASON else 0.0
    table_penalty = 0.15 if query_kind is QueryKind.DECISION_REASON and "결정 결과" in content else 0.0
    return overlap + (0.25 * identifier) + reason_bonus - table_penalty


def _rank_signal(rank: int | None) -> float:
    return 0.0 if rank is None else 1.0 / rank**0.5


def _title_phrase_overlap(query: str, item: StoredChunk) -> float:
    """Reward a document title that appears as a phrase in the query."""
    compact_query = re.sub(r"\W+", "", query.casefold())
    compact_title = re.sub(r"\W+", "", item.title.casefold())
    if len(compact_title) < 4 or compact_title not in compact_query:
        return 0.0
    return 0.35


def _token_overlap(query: str, item: StoredChunk) -> float:
    terms = set(tokenize(query))
    if not terms:
        return 0.0
    item_terms = set(tokenize(f"{item.title} {item.source_path} {item.content}"))
    return len(terms & item_terms) / len(terms)


def _identifier_overlap(query: str, item: StoredChunk) -> float | None:
    identifiers = _identifier_terms(query)
    if not identifiers:
        return None
    item_terms = set(tokenize(f"{item.title} {item.source_path} {item.content}"))
    return len(identifiers & item_terms) / len(identifiers)


def _identifier_terms(query: str) -> set[str]:
    return {
        token
        for token in tokenize(query)
        if len(token) >= 3 and token.isascii() and any(character.isalpha() for character in token)
    }


def _missing_identifier_evidence(query: str, candidates: tuple[StoredChunk, ...]) -> bool:
    identifiers = _identifier_terms(query)
    return bool(identifiers) and not any(
        identifier in set(tokenize(f"{item.source_path} {item.content}"))
        for item in candidates
        for identifier in identifiers
    )


def _has_conflict_evidence(query: str, item: StoredChunk) -> bool:
    identifiers = _identifier_terms(query)
    dates = set(re.findall(r"\d+월|\d+일", query))
    item_terms = set(tokenize(f"{item.title} {item.source_path} {item.content}"))
    return bool(identifiers & item_terms) and bool(dates & item_terms)


def _score_margin(query_kind: QueryKind) -> float:
    return {
        QueryKind.FACT: 0.1,
        QueryKind.AMBIGUOUS: 0.25,
        QueryKind.DECISION_REASON: 0.1,
        QueryKind.MEETING_DATE: 0.1,
        QueryKind.CONFLICT: 0.15,
        QueryKind.BROAD: 0.0,
    }[query_kind]
