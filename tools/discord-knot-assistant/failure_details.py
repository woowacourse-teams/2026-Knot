from __future__ import annotations

from dataclasses import dataclass
from typing import assert_never

import httpx2
from pydantic import ValidationError

from settings import AssistantFailure


@dataclass(frozen=True, slots=True)
class FailureDetails:
    code: str
    message: str


def describe_failure(error: AssistantFailure | httpx2.HTTPError | ValidationError, stage: str) -> FailureDetails:
    match error:
        case AssistantFailure(code=code, user_message=user_message):
            details = {
                "notion_token_missing": "Mac에 Notion 액세스 키가 저장되지 않았어.",
                "notion_http_401": "Notion 액세스 키가 유효하지 않아.",
                "notion_http_403": "Notion 연결에 이 문서를 읽을 권한이 없어.",
                "notion_http_404": "Notion 페이지를 찾지 못했거나 연결에 공유되지 않았어.",
                "notion_http_429": "Notion 요청 한도에 걸렸어.",
                "notion_outside_knot": "요청한 문서는 Knot 하위 페이지·DB 범위 밖이라 읽지 않았어.",
                "notion_timeout": "Notion 응답 시간이 초과됐어.",
                "notion_connection_failed": "Notion API에 연결하지 못했어.",
                "notion_mcp_connection_failed": "Mac의 Notion MCP 연결이 끊겼거나 응답 시간이 초과됐어.",
                "notion_mcp_response_invalid": "Notion MCP의 반환 형식을 읽지 못했어.",
                "notion_read_limit": "Notion 직접 조회의 요청 한도에 도달했어.",
                "notion_invalid_id": "Notion 페이지·DB ID 형식이 잘못됐어.",
                "notion_search_query_needed": "검색할 문서 제목이나 Notion 페이지 링크가 필요해.",
                "codex_timeout": "Codex 답변 생성 시간이 초과됐어.",
                "codex_cli_missing": "Mac에서 Codex CLI를 찾지 못했어.",
                "codex_empty_response": "Codex가 답변을 반환하지 않았어.",
            }
            detail = user_message or details.get(code, f"처리를 완료하지 못했어. 오류 코드: {code}")
        case httpx2.HTTPStatusError(response=response):
            code = f"http_{response.status_code}"
            detail = f"서버가 HTTP {response.status_code} 오류를 반환했어."
        case httpx2.TimeoutException():
            code, detail = "http_timeout", "서버 응답 시간이 초과됐어."
        case httpx2.HTTPError():
            code, detail = "http_connection_failed", "서버 연결에 실패했어."
        case ValidationError():
            code, detail = "response_invalid", "반환된 데이터 형식을 읽지 못했어."
        case unreachable:
            assert_never(unreachable)
    return FailureDetails(code, f"{stage} 단계에서 실패했어. {detail}")
