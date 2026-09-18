from __future__ import annotations

from pgvector_rag import rank_hybrid_candidates
from pgvector_store import StoredChunk
from retrieval_policy import QueryKind, plan_query


def _chunk(path: str, title: str, content: str = "PostgreSQL 문서") -> StoredChunk:
    return StoredChunk(path, title, content, 0.8)


def test_plan_query_classifies_decision_questions_and_broad_questions() -> None:
    decision = plan_query("PostgreSQL을 왜 쓰기로 했어?", ())
    broad = plan_query("우리 프로젝트 어떻게 진행되고 있어?", ())

    assert decision.kind is QueryKind.DECISION_REASON
    assert not decision.should_clarify
    assert broad.kind is QueryKind.BROAD
    assert broad.should_clarify


def test_plan_query_classifies_conflicting_naming_question_as_ambiguous() -> None:
    plan = plan_query("camelCase랑 snake_case 중 어떤 걸 써?", ())

    assert plan.kind is QueryKind.AMBIGUOUS


def test_plan_query_rewrites_deictic_follow_up_with_previous_question() -> None:
    plan = plan_query("그 회의에서 뭘 정했어?", ("로드맵 회의 언제 했지?",))

    assert plan.search_query.startswith("로드맵 회의 언제 했지? 그 회의에서 뭘 정했어?")
    assert "결정 사항" in plan.search_query
    assert plan.kind is QueryKind.MEETING_DATE

    folder_plan = plan_query("폴더구조 회의에서 뭘 정했어?", ())
    assert "결정 사항" in folder_plan.search_query

    location_plan = plan_query("폴더구조 컨벤션 회의는 언제였고 문서 어디 있어?", ())
    assert location_plan.kind is QueryKind.MEETING_DATE


def test_holdout_query_does_not_receive_gold_answer_terms() -> None:
    holdout_queries = (
        "로드맵에서 다음 분기에 뭘 정했어?",
        "폴더 구조 회의에서 어떤 기준을 정했어?",
        "알림 저장소에서 어떤 방식을 선택했어?",
    )

    for question in holdout_queries:
        search_query = plan_query(question, ()).search_query.casefold()

        assert "흑곰" not in search_query
        assert "shared/hooks/domain" not in search_query
        assert "level 3" not in search_query
        assert "레벨 3" not in search_query


def test_plan_query_marks_related_document_follow_up() -> None:
    plan = plan_query("관련된 거 더 알려줘.", ("PostgreSQL을 왜 쓰기로 했어?",))

    assert plan.related_documents
    assert "PostgreSQL" in plan.search_query
    assert plan.kind is QueryKind.DECISION_REASON


def test_hybrid_rerank_unions_keyword_only_authoritative_source() -> None:
    vector_candidates = (
        _chunk(
            "기능 요구 사항/결과 기반 관련 문서 추천 496de1156a83823da76a8156f2726936.md",
            "결과 기반 관련 문서 추천",
            "PostgreSQL을 사용한 이유는 팀원들의 사용 경험이다.",
        ),
        _chunk("BE 위키/09 패키지 구조.md", "패키지 구조"),
    )
    keyword_candidates = (
        _chunk(
            "백엔드/01 기술 스택과 라이브러리 도입 fffde1156a83837097bc818fab8a1fa4.md",
            "01. 기술 스택과 라이브러리 도입",
            "관계형 데이터를 안정적으로 관리하고 pgvector로 확장할 수 있어 PostgreSQL을 선택했다.",
        ),
        vector_candidates[0],
    )

    selected = rank_hybrid_candidates(
        "PostgreSQL을 왜 쓰기로 했어?",
        vector_candidates,
        keyword_candidates,
        QueryKind.DECISION_REASON,
        3,
    )

    assert selected[0].source_path.startswith("백엔드/01 기술 스택과")
    assert len({item.source_path for item in selected}) == len(selected)


def test_hybrid_rerank_limits_a_single_subject_fact_to_one_source() -> None:
    # Given: the answer source and a nearby database-rules document
    primary = _chunk(
        "백엔드/01 기술 스택과 라이브러리 도입.md",
        "기술 스택",
        "PostgreSQL을 사용하기로 결정했다.",
    )
    related = _chunk(
        "백엔드/05 데이터베이스와 Migration 규칙.md",
        "데이터베이스와 Migration 규칙",
        "PostgreSQL 테이블은 snake_case로 작성한다.",
    )

    # When: a fact question asks for one project-level decision
    selected = rank_hybrid_candidates(
        "우리 백엔드 DB 뭐 쓰기로 했지?",
        (primary, related),
        (),
        QueryKind.FACT,
        3,
    )

    # Then: a nearby document is not presented as an additional answer source
    assert tuple(item.source_path for item in selected) == (primary.source_path,)


def test_hybrid_rerank_keeps_two_sources_for_an_ambiguous_comparison() -> None:
    # Given: two naming rules and an unrelated third convention document
    database = _chunk(
        "백엔드/05 데이터베이스와 Migration 규칙.md",
        "데이터베이스와 Migration 규칙",
        "DB 테이블과 컬럼은 snake_case를 사용한다.",
    )
    java = _chunk(
        "백엔드/06 Java 코드 작성 규칙.md",
        "Java 코드 작성 규칙",
        "Java 메서드와 변수는 camelCase를 사용한다.",
    )
    unrelated = _chunk(
        "프론트/코드 컨벤션.md",
        "프론트 코드 컨벤션",
        "컴포넌트 규칙을 정의한다.",
    )

    # When: the question explicitly asks which of two rules applies
    selected = rank_hybrid_candidates(
        "camelCase랑 snake_case 중 어떤 걸 써?",
        (database, java, unrelated),
        (),
        QueryKind.AMBIGUOUS,
        3,
    )

    # Then: both subjects remain while the unrelated third source is excluded
    assert {item.source_path for item in selected} == {
        database.source_path,
        java.source_path,
    }


def test_hybrid_rerank_does_not_expand_same_title_meeting_duplicates() -> None:
    # Given: two exported pages with the same meeting title
    first = _chunk(
        "회의록/폴더구조 컨벤션 회의 da1de1156a8383a09397012c78e7ee84.md",
        "폴더구조 컨벤션 회의",
        "2026년 8월 14일 회의 내용",
    )
    duplicate = _chunk(
        "회의록/폴더구조 컨벤션 회의 c5ade1156a838221978e816f5cd57c4a.md",
        "폴더구조 컨벤션 회의",
        "다른 내보내기 문서",
    )

    # When: a date/location question asks for the matching meeting
    selected = rank_hybrid_candidates(
        "폴더구조 컨벤션 회의는 언제였고 문서 어디 있어?",
        (first, duplicate),
        (),
        QueryKind.MEETING_DATE,
        3,
    )

    # Then: same-title duplicates do not inflate the related-document list
    assert tuple(item.source_path for item in selected) == (first.source_path,)


def test_hybrid_rerank_prioritizes_meeting_source_for_date_question() -> None:
    vector_candidates = (
        _chunk("쓰레기통/로드맵 정리.md", "로드맵 정리"),
        _chunk("회의록/로드맵 기반 기획 회의 453de1156a838282959681990c718da2.md", "로드맵 기반 기획 회의"),
    )

    selected = rank_hybrid_candidates(
        "로드맵 기반 기획 회의 언제 했지?",
        vector_candidates,
        (),
        QueryKind.MEETING_DATE,
        1,
    )

    assert selected[0].source_path.startswith("회의록/로드맵 기반 기획 회의")


def test_hybrid_rerank_prefers_exact_decision_subject_over_generic_authority() -> None:
    vector_candidates = (
        _chunk("백엔드/01 기술 스택과 라이브러리 도입.md", "기술 스택", "관계형 데이터베이스를 사용한다."),
        _chunk("회의록/인터뷰 정리 & 로드맵 구성.md", "인터뷰 정리", "세션을 공유해야 해서 Redis를 선택했다."),
    )

    selected = rank_hybrid_candidates(
        "세션 저장소로 Redis를 선택한 이유가 뭐야?",
        vector_candidates,
        (),
        QueryKind.DECISION_REASON,
        1,
    )

    assert selected[0].source_path.startswith("회의록/인터뷰 정리")


def test_hybrid_rerank_prefers_conflict_evidence_over_date_only_match() -> None:
    vector_candidates = (
        _chunk("회의록/제목 없음.csv", "회의록", "2026년 8월 3일 회의록"),
        _chunk("기능 요구 사항/자연어 기반 AI 정보 탐색.md", "자연어 기반 AI 정보 탐색", "8월 3일은 snake_case, 8월 10일은 camelCase로 기록됐다."),
    )

    selected = rank_hybrid_candidates(
        "8월 3일 회의록은 snake_case고 8월 10일 컨벤션 문서는 camelCase라는데 뭐가 맞아?",
        vector_candidates,
        (),
        QueryKind.CONFLICT,
        1,
    )

    assert selected[0].source_path.startswith("기능 요구 사항/자연어 기반")


def test_related_document_request_keeps_three_high_value_backend_sources() -> None:
    candidates = (
        _chunk("인프라/AWS/RDS PostgreSQL 연결.md", "RDS PostgreSQL 연결", "PostgreSQL 운영 연결"),
        _chunk("백엔드/01 기술 스택과 라이브러리 도입.md", "기술 스택", "PostgreSQL을 선택한 이유"),
        _chunk("백엔드/05 데이터베이스와 Migration 규칙.md", "데이터베이스와 Migration 규칙", "PostgreSQL 규칙"),
        _chunk("백엔드/07 테스트 전략과 기준.md", "테스트 전략과 기준", "PostgreSQL 테스트"),
    )

    selected = rank_hybrid_candidates(
        "PostgreSQL을 왜 쓰기로 했어? 관련된 거 더 알려줘.",
        candidates,
        (),
        QueryKind.DECISION_REASON,
        3,
        related_documents=True,
    )

    paths = tuple(item.source_path for item in selected)
    assert paths[0].startswith("백엔드/01 기술 스택과")
    assert any(path.startswith("백엔드/05 데이터베이스") for path in paths)
    assert any(path.startswith("백엔드/07 테스트 전략") for path in paths)


def test_hybrid_rerank_uses_keyword_chunk_when_vector_chunk_is_less_specific() -> None:
    path = "회의록/폴더구조 컨벤션 회의 da1de1156a8383a09397012c78e7ee84.md"
    vector_candidates = (_chunk(path, "폴더구조 컨벤션 회의", "AI 사용 비중과 회의 진행 방식"),)
    keyword_candidates = (
        _chunk(path, "폴더구조 컨벤션 회의", "미해결 쟁점: shared/hooks/domain과 feature 중 배치 기준"),
    )

    selected = rank_hybrid_candidates(
        "폴더구조 컨벤션 회의에서 뭘 정했어?",
        vector_candidates,
        keyword_candidates,
        QueryKind.FACT,
        1,
    )

    assert "미해결 쟁점" in selected[0].content


def test_hybrid_rerank_uses_reason_passage_over_decision_result_table() -> None:
    path = "백엔드/01 기술 스택과 라이브러리 도입 fffde1156a83837097bc818fab8a1fa4.md"
    vector_candidates = (_chunk(path, "기술 스택", "## 3. 결정 결과\n| 데이터베이스 | PostgreSQL |"),)
    keyword_candidates = (_chunk(path, "기술 스택", "### PostgreSQL\n관계형 데이터를 안정적으로 관리하고 pgvector로 확장한다."),)

    selected = rank_hybrid_candidates(
        "PostgreSQL을 왜 쓰기로 했어?",
        vector_candidates,
        keyword_candidates,
        QueryKind.DECISION_REASON,
        1,
    )

    assert "관계형 데이터를" in selected[0].content


def test_hybrid_rerank_prefers_specific_folder_decision_passage() -> None:
    vector_candidates = (
        _chunk("회의록/폴더 구조 컨벤션 논의.md", "폴더 구조 컨벤션 논의", "폴더 구조를 논의했다."),
        _chunk(
            "회의록/폴더구조 컨벤션 회의.md",
            "폴더구조 컨벤션 회의",
            "합의된 규칙과 미해결 핵심 쟁점: shared/hooks/domain과 feature",
        ),
    )

    selected = rank_hybrid_candidates(
        "폴더구조 컨벤션 회의에서 뭘 정했어?",
        vector_candidates,
        (),
        QueryKind.DECISION_REASON,
        1,
    )

    assert selected[0].source_path.startswith("회의록/폴더구조 컨벤션 회의")


def test_hybrid_rerank_prefers_exact_meeting_title_over_related_discussion_title() -> None:
    vector_candidates = (
        _chunk("회의록/폴더 구조 컨벤션 논의.md", "폴더 구조 컨벤션 논의", "폴더 구조를 논의했다."),
        _chunk("회의록/폴더구조 컨벤션 회의.md", "폴더구조 컨벤션 회의", "합의된 규칙과 미해결 핵심 쟁점"),
    )

    selected = rank_hybrid_candidates(
        "폴더구조 컨벤션 회의에서 뭘 정했어?",
        vector_candidates,
        (),
        QueryKind.DECISION_REASON,
        1,
    )

    assert selected[0].title == "폴더구조 컨벤션 회의"
