"""Lightweight answer-shape checks for the benchmark gold set."""

from __future__ import annotations


def answer_shape_passes(answer: str, case_id: str, turn: int) -> bool:
    """Check required facts without pretending to replace human review."""
    normalized = answer.casefold()
    if case_id == "G-011":
        return _has_group(normalized, (("확인할 수 없", "찾지 못", "없습니다"),))
    if case_id == "G-012":
        return _has_group(normalized, (("범위가 넓어요",), ("최근 결정사항",), ("로드맵",), ("백엔드",)))
    return _has_group(normalized, _GROUPS.get((case_id, turn), ()))


_GROUPS: dict[tuple[str, int], tuple[tuple[str, ...], ...]] = {
    ("G-001", 1): (("postgresql",), ("jpa", "spring data jpa"), ("flyway", "migration")),
    ("G-002", 1): (("snake_case",), ("복수형", "복수"), ("단수형", "단수")),
    ("G-003", 1): (("postgresql",), ("관계형", "relational"), ("pgvector", "벡터 검색", "벡터검색")),
    ("G-004", 1): (("redis",), ("중앙", "central"), ("확장", "여러 서버", "여러서버"), ("8월 19일", "2026-08-19")),
    ("G-005", 1): (("2026년 8월 19일", "8월 19일", "2026-08-19"), ("로드맵",), ("인터뷰",)),
    ("G-006", 1): (("2026년 8월 14일", "8월 14일", "2026-08-14"), ("widget", "위젯"), ("feature", "피처"), ("shared/hooks", "shared vs feature", "훅"), ("미결", "확정되지", "논의")),
    ("G-007", 2): (("로드맵",), ("level 3", "level3", "레벨 3", "레벨3"), ("기능", "feature"), ("흑곰", "기획안", "요구사항")),
    ("G-008", 2): (("미결", "확정되지", "아직"), ("shared/hooks/domain", "shared/hooks"), ("feature",), ("두 번째", "두번째", "2회째", "여러 화면")),
    ("G-009", 1): (("snake_case",), ("camelcase",), ("테이블", "컬럼", "database"), ("java", "메서드", "변수")),
    ("G-010", 1): (("snake_case",), ("camelcase",), ("확정", "충돌", "다르게", "어렵")),
    ("G-013", 1): (("postgresql",), ("관계형", "relational"), ("pgvector", "벡터 검색", "벡터검색")),
    ("G-013", 2): (("추가", "관련", "문서"), ("postgresql", "database", "데이터베이스", "migration", "테스트", "pgvector")),
}


def _has_group(answer: str, groups: tuple[tuple[str, ...], ...]) -> bool:
    return all(any(pattern.casefold() in answer for pattern in group) for group in groups)
