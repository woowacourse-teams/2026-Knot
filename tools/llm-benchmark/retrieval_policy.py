"""Deterministic query planning and document-authority signals for RAG."""

from __future__ import annotations

import re
from dataclasses import dataclass
from enum import StrEnum
from typing import Final


class QueryKind(StrEnum):
    """Question shapes that need different retrieval priorities."""

    FACT = "fact"
    AMBIGUOUS = "ambiguous"
    DECISION_REASON = "decision_reason"
    MEETING_DATE = "meeting_date"
    CONFLICT = "conflict"
    BROAD = "broad"


@dataclass(frozen=True, slots=True)
class QueryPlan:
    """A normalized retrieval request plus deterministic safety decisions."""

    original_query: str
    search_query: str
    kind: QueryKind
    should_clarify: bool
    related_documents: bool

    @property
    def clarification_text(self) -> str:
        """Return the fixed response for a deliberately broad request."""
        return "범위가 넓어요. 최근 결정사항, 로드맵, 백엔드 진행 상황 중 어떤 내용을 찾고 싶나요?"


_BROAD_PATTERNS: Final[tuple[str, ...]] = (
    "어떻게 진행",
    "전체 요약",
    "전체적으로",
    "프로젝트 현황",
    "프로젝트 전반",
    "무슨 일",
)
_CONFLICT_PATTERNS: Final[tuple[str, ...]] = (
    "뭐가 맞",
    "어떤 게 맞",
    "어느 게 맞",
    "다르게 기록",
    "충돌",
    "snake_case.*camelCase",
    "camelCase.*snake_case",
)
_DECISION_PATTERNS: Final[tuple[str, ...]] = (
    "왜",
    "이유",
    "선택한",
    "사용하기로",
    "정한 이유",
    "확정했",
    "뭘 정했",
    "무엇을 정했",
    "결정했",
)
_MEETING_PATTERNS: Final[tuple[str, ...]] = ("언제", "몇 월", "회의 날짜", "회의록")
_RELATED_PATTERNS: Final[tuple[str, ...]] = ("관련된", "관련 문서", "더 알려", "더 보여")
_CONTEXT_PATTERNS: Final[tuple[str, ...]] = (
    "그 ",
    "그럼 ",
    "이 회의",
    "저 회의",
    "그 회의",
    "그것",
    "그거",
    "앞서",
    "이전",
)


def plan_query(query: str, previous_queries: tuple[str, ...]) -> QueryPlan:
    """Rewrite a contextual follow-up and classify its retrieval policy."""
    original = query.strip()
    related_documents = _contains_any(original, _RELATED_PATTERNS)
    anchor = _last_non_empty(previous_queries)
    search_query = _expand_decision_summary(_rewrite(original, anchor, related_documents))
    kind = classify_query(search_query)
    return QueryPlan(original, search_query, kind, kind is QueryKind.BROAD, related_documents)


def classify_query(query: str) -> QueryKind:
    """Classify a question using stable lexical signals."""
    normalized = query.strip()
    if _contains_any(normalized, _BROAD_PATTERNS):
        return QueryKind.BROAD
    if (
        "camelcase" in normalized.casefold()
        and "snake_case" in normalized.casefold()
        and _contains_any(normalized, ("중 어떤", "어떤 걸", "무엇을"))
    ):
        return QueryKind.AMBIGUOUS
    if _contains_pattern(normalized, _CONFLICT_PATTERNS):
        return QueryKind.CONFLICT
    if "camelcase" in normalized.casefold() and "snake_case" in normalized.casefold():
        return QueryKind.AMBIGUOUS
    if _contains_any(normalized, _MEETING_PATTERNS):
        return QueryKind.MEETING_DATE
    if _contains_any(normalized, _DECISION_PATTERNS):
        return QueryKind.DECISION_REASON
    return QueryKind.FACT


def authority_score(source_path: str, title: str, kind: QueryKind, query: str = "") -> float:
    """Score how likely a page is to be an authoritative answer source."""
    haystack = f"{source_path} {title}".casefold()
    if _contains_any(haystack, ("커피챗", "잡다한", "쓰레기통")):
        return 0.15
    if kind is QueryKind.MEETING_DATE and _contains_any(haystack, ("회의록", "회의")):
        return 1.0
    if kind is QueryKind.CONFLICT:
        if _contains_any(haystack, ("회의록", "회의", "컨벤션", "규칙", "정책", "요구 사항", "요구사항")):
            return 0.75
        return 0.55
    if kind is QueryKind.AMBIGUOUS and _contains_any(haystack, ("기술 스택", "규칙", "컨벤션", "정책")):
        return 0.85
    if kind is QueryKind.DECISION_REASON and "인터뷰" in haystack and "회의록" not in haystack:
        return 0.2
    if _contains_any(haystack, ("요구 사항", "요구사항", "스크럼", "일지")):
        return 0.2
    if _contains_any(haystack, ("기술 스택", "결정 기록", "adr", "선정", "정책", "규칙")):
        return 1.0
    if _contains_any(haystack, ("회의록", "회의")):
        return 0.82
    if _contains_any(haystack, ("테스트 전략",)):
        return 0.9
    if _contains_any(haystack, ("위키", "명세")):
        return 0.55
    if _contains_any(haystack, ("요구 사항", "요구사항", "커피챗", "쓰레기통", "잡다한", "스크럼", "일지")):
        return 0.24
    return 0.5


def _rewrite(query: str, anchor: str, related_documents: bool) -> str:
    if not anchor or not (related_documents or _contains_any(query, _CONTEXT_PATTERNS) or len(query) <= 24):
        return query
    return f"{anchor} {query}".strip()


def _expand_decision_summary(query: str) -> str:
    if not _contains_any(query, ("뭘 정했", "무엇을 정했", "뭘 결정", "무엇을 결정")):
        return query
    return f"{query} 결정 사항 합의된 규칙 논의 미결".strip()


def _last_non_empty(values: tuple[str, ...]) -> str:
    return next((value.strip() for value in reversed(values) if value.strip()), "")


def _contains_any(value: str, patterns: tuple[str, ...]) -> bool:
    return any(pattern.casefold() in value.casefold() for pattern in patterns)


def _contains_pattern(value: str, patterns: tuple[str, ...]) -> bool:
    return any(re.search(pattern, value, flags=re.IGNORECASE) is not None for pattern in patterns)
