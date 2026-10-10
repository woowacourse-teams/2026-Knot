import type { PostSearchQuestionRequestDto } from "@api/dto/search";
import { toSearchStreamEvent } from "@api/fetch/api/v1/workspaces/[workspaceId]/search/conversations";
import { requestSseStream } from "@api/sse/requestSseStream";

export const WORKSPACE_SEARCH_CONVERSATION_QUESTIONS_API_PATH = (
  workspaceId: number,
  conversationId: number,
) =>
  `/api/v1/workspaces/${workspaceId}/search/conversations/${conversationId}/questions`;

interface CreateSearchQuestionApiParams {
  workspaceId: number;
  conversationId: number;
  body: PostSearchQuestionRequestDto;
  signal?: AbortSignal;
}

/**
 * @description 이어 가던 탐색 대화에 후속 질문을 보내고 SSE로 도착하는 답변 이벤트를 순서대로 흘려보내요
 * @param params - 워크스페이스 ID, 탐색 대화 ID, 후속 질문 전송 요청 본문, 취소용 AbortSignal
 * @returns accepted·progress·delta·evidence·excluded_documents·completed·failed·stopped 이벤트를 순서대로 내는 async generator. 받는 쪽이 중간에 멈추면 스트림 읽기를 정리해요
 * @throws {SseRequestError} accepted 이벤트 전에 실패 응답을 받은 경우. `error.code === "SEARCH_REQUEST_CONFLICT"`처럼 서버 오류 코드로 분기해요
 * @example
 * for await (const { event, data } of createSearchQuestionApi({ workspaceId: 1, conversationId: 100, body })) {
 *   if (event === "delta") answer += data.text;
 * }
 */
export async function* createSearchQuestionApi({
  workspaceId,
  conversationId,
  body,
  signal,
}: CreateSearchQuestionApiParams) {
  for await (const sseEvent of requestSseStream({
    method: "POST",
    path: WORKSPACE_SEARCH_CONVERSATION_QUESTIONS_API_PATH(
      workspaceId,
      conversationId,
    ),
    body,
    signal,
  })) {
    const searchStreamEvent = toSearchStreamEvent(sseEvent);
    if (searchStreamEvent) yield searchStreamEvent;
  }
}
