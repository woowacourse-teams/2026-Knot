from __future__ import annotations

import re
from enum import StrEnum
from typing import Annotated, Final

from pydantic import BaseModel, ConfigDict, Field, StringConstraints

class ReasoningEffort(StrEnum):
    LOW = "low"
    MEDIUM = "medium"
    MAX = "max"


class QuestionRoute(BaseModel):
    model_config = ConfigDict(frozen=True, extra="forbid", strict=True)

    effort: ReasoningEffort
    retrieve_notion: bool


SearchQuery = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=40)]


class QuestionPreparation(QuestionRoute):
    intent: Annotated[str, StringConstraints(min_length=1, max_length=300)]
    search_queries: Annotated[list[SearchQuery], Field(max_length=4)]


COMPLEX_QUESTION: Final = re.compile(
    r"설계|아키텍처|디버[깅그]|리팩터|리팩토|원인.{0,12}(?:분석|추적)|"
    r"분석|비교|트레이드\s*오프|장단점|해결\s*(?:방안|방법)|"
    r"깊게|꼼꼼|자세히|동시성|데드락|마이그레이션|"
    r"\b(?:design|architecture|debug\w*|refactor\w*|analy[sz]\w*|compare|trade.?offs?)\b",
    re.IGNORECASE,
)
DOCUMENT_QUESTION: Final = re.compile(
    r"knot|노션|notion|문서|스프린트|스크럼|그라운드\s*룰|정책|"
    r"요구사항|일정|목표|회의|담당|이슈|개발|코드|버그|배포|"
    r"데이터베이스|\b(?:db|api|issue|sprint|code|bug)\b",
    re.IGNORECASE,
)
CASUAL_QUESTION: Final = re.compile(
    r"(?:안녕\w*|하이|ㅎㅇ+|hi|hello|좋은\s*아침|굿모닝|잘\s*자|"
    r"고마워\w*|감사(?:합니다|해\w*)?|ㅋㅋ+|ㅎㅎ+|오랜만\w*|"
    r"(?:오늘\s*|지금\s*)?뭐\s*(?:해\w*|하니|하고\s*있어)|"
    r"반가워\w*|심심\w*|배고[파프]\w*|졸[려리]\w*)"
    r"[!?.~ㅋㅎ\s]*",
    re.IGNORECASE,
)


def route_question(question: str) -> QuestionRoute:
    """Choose effort locally; only clear casual messages skip document retrieval."""
    normalized = " ".join(question.split())
    if COMPLEX_QUESTION.search(normalized) is not None or len(normalized) > 600:
        return QuestionRoute(effort=ReasoningEffort.MAX, retrieve_notion=True)
    if DOCUMENT_QUESTION.search(normalized) is not None:
        return QuestionRoute(effort=ReasoningEffort.MEDIUM, retrieve_notion=True)
    if not normalized or CASUAL_QUESTION.fullmatch(normalized) is not None:
        return QuestionRoute(effort=ReasoningEffort.LOW, retrieve_notion=False)
    return QuestionRoute(effort=ReasoningEffort.MEDIUM, retrieve_notion=True)
