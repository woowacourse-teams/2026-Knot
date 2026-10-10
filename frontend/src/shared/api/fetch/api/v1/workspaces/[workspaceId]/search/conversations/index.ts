import {
  SearchStreamAcceptedDto,
  SearchStreamCompletedDto,
  SearchStreamDeltaDto,
  SearchStreamEvidenceDto,
  SearchStreamExcludedDocumentsDto,
  SearchStreamFailedDto,
  SearchStreamProgressDto,
  SearchStreamStoppedDto,
  type PostSearchConversationRequestDto,
  type SearchStreamEvent,
} from "@api/dto/search";
import type { SseEvent } from "@api/sse/parseSseEvents";
import { requestSseStream } from "@api/sse/requestSseStream";

export const WORKSPACE_SEARCH_CONVERSATIONS_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/search/conversations`;

/** 명세에 있는 이벤트만 DTO로 감싸요. 모르는 이벤트 이름은 버려요 */
export const toSearchStreamEvent = ({
  event,
  data,
}: SseEvent): SearchStreamEvent | null => {
  // case마다 event가 리터럴로 좁혀져 data DTO와 짝이 맞는지 타입이 확인해 줘요
  switch (event) {
    case "accepted":
      return { event, data: new SearchStreamAcceptedDto(JSON.parse(data)) };
    case "progress":
      return { event, data: new SearchStreamProgressDto(JSON.parse(data)) };
    case "delta":
      return { event, data: new SearchStreamDeltaDto(JSON.parse(data)) };
    case "evidence":
      return { event, data: new SearchStreamEvidenceDto(JSON.parse(data)) };
    case "excluded_documents":
      return {
        event,
        data: new SearchStreamExcludedDocumentsDto(JSON.parse(data)),
      };
    case "completed":
      return { event, data: new SearchStreamCompletedDto(JSON.parse(data)) };
    case "failed":
      return { event, data: new SearchStreamFailedDto(JSON.parse(data)) };
    case "stopped":
      return { event, data: new SearchStreamStoppedDto(JSON.parse(data)) };
    default:
      return null;
  }
};

interface CreateSearchConversationApiParams {
  workspaceId: number;
  body: PostSearchConversationRequestDto;
  signal?: AbortSignal;
}

/**
 * @description 첫 질문으로 탐색 대화를 시작하고 SSE로 도착하는 답변 이벤트를 순서대로 흘려보내요
 * @param params - 워크스페이스 ID, 첫 질문 전송 요청 본문, 취소용 AbortSignal
 * @returns accepted·progress·delta·evidence·excluded_documents·completed·failed·stopped 이벤트를 순서대로 내는 async generator. 받는 쪽이 중간에 멈추면 스트림 읽기를 정리해요
 * @throws {SseRequestError} accepted 이벤트 전에 실패 응답을 받은 경우. `error.code === "SEARCH_REQUEST_CONFLICT"`처럼 서버 오류 코드로 분기해요
 * @example
 * for await (const { event, data } of createSearchConversationApi({ workspaceId: 1, body })) {
 *   if (event === "delta") answer += data.text;
 * }
 */
export async function* createSearchConversationApi({
  workspaceId,
  body,
  signal,
}: CreateSearchConversationApiParams) {
  for await (const sseEvent of requestSseStream({
    method: "POST",
    path: WORKSPACE_SEARCH_CONVERSATIONS_API_PATH(workspaceId),
    body,
    signal,
  })) {
    const searchStreamEvent = toSearchStreamEvent(sseEvent);
    if (searchStreamEvent) yield searchStreamEvent;
  }
}
