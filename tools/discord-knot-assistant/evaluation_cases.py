from __future__ import annotations

import re
from typing import Final

from pydantic import BaseModel, ConfigDict

from settings import NotionLookup


class EvaluationCase(BaseModel):
    model_config = ConfigDict(frozen=True)

    name: str
    question: str
    criteria: str
    reference_id: str | None = None
    retrieve_notion: bool = True


INVITATION: Final = "496b4351752283a9b3d90139067890f5"
COMMIT: Final = "3b3b43517522801b882dce1bf798a52b"
CASES: Final = (
    EvaluationCase(
        name="invitation_modes",
        question="현재 프로젝트 초대장 방식은 단일 모드/복수 모드 중 무엇인가? 단일은 하나의 유효 초대를 다시 보여주고, 복수는 새 초대를 생성해도 기존 초대가 유지되는 방식이야.",
        criteria="정책은 복수 모드라는 결론. 기존 초대를 무효화하지 않는 근거. 여러 유효 초대 생성은 후속 구현이라는 구분. 구현 완료라고 주장하면 실패.",
        reference_id=INVITATION,
    ),
    EvaluationCase(
        name="invitation_paraphrase",
        question="새 사람 초대하면 전에 공유한 코드 못 쓰게 돼? Knot 기준으로 알려줘",
        criteria="새 초대를 만들어도 기존 초대는 유지된다고 답한다. 만료나 구현 상태를 언급하면 문서와 일치해야 한다.",
        reference_id=INVITATION,
    ),
    EvaluationCase(
        name="false_premise",
        question="Knot 워크스페이스 유효 초대는 항상 1개니까 새로 발급하면 기존 건 폐기되는 거 맞지?",
        criteria="사용자의 잘못된 전제를 명확하게 정정. 새 발급이 기존 초대를 무효화하지 않는 정책. 현재 구현 완료를 추측하지 않음.",
        reference_id=INVITATION,
    ),
    EvaluationCase(
        name="unknown_deployment",
        question="Knot의 복수 초대 정책이 실제 운영 서버에 배포된 정확한 시각이 언제야?",
        criteria="정확한 실제 배포 시각을 확인할 수 없다고 답한다. 문서 작성일/정책 확정일을 운영 배포 시각으로 바꾸거나 가상의 시각을 만들어내면 실패.",
        reference_id=INVITATION,
    ),
    EvaluationCase(
        name="casual_chat",
        question="하이 ㅋㅋ 오늘 뭐해?",
        criteria="짧고 자연스러운 한국어 잡담. 문서 조회나 실패 안내, 프로젝트 정책, 참고 문서가 없어야 한다.",
        retrieve_notion=False,
    ),
    EvaluationCase(
        name="commit_convention",
        question="Knot 프론트엔드 커밋 타입 컨벤션 알려줘",
        criteria="실제 문서의 feat, fix, docs, refactor, chore를 설명. 문서에 없는 필수 타입/규칙을 창작하지 않음. 커밋 컨벤션 근거를 인용.",
        reference_id=COMMIT,
    ),
)


class Review(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)

    passed: bool
    issues: list[str]


def check_answer(answer: str, lookup: NotionLookup, case: EvaluationCase, retrieved: bool) -> list[str]:
    issues: list[str] = []
    if retrieved != case.retrieve_notion:
        issues.append("retrieval_route_mismatch")
    if not answer.strip():
        issues.append("empty_answer")
    if len(answer) > 1500:
        issues.append("answer_too_long")
    if re.search(r"n8n|MCP|HTTP\s*\d|검색 후보|조회 단계에서 실패", answer, re.IGNORECASE):
        issues.append("recovered_error_leaked")
    links = re.findall(r"https?://[^\s)\]>]+", answer)
    source_numbers = [int(value) for value in re.findall(r"\[출처\s*(\d+)\]", answer)]
    allowed = {str(source.url) for source in lookup.sources}
    if any(url not in allowed for url in links) or any(index < 1 or index > len(lookup.sources) for index in source_numbers):
        issues.append("unsupported_citation")
    if case.reference_id is not None and not links and not source_numbers:
        issues.append("missing_citation")
    if not case.retrieve_notion and (links or source_numbers):
        issues.append("casual_chat_has_citation")
    return issues
