import {
  PostSearchConversationErrorResponseDto,
  SearchStreamAcceptedDto,
  SearchStreamCompletedDto,
  SearchStreamDeltaDto,
  SearchStreamEvidenceDto,
  SearchStreamExcludedDocumentsDto,
  SearchStreamFailedDto,
  SearchStreamProgressDto,
  SearchStreamStoppedDto,
  type PostSearchConversationErrorResponseRaw,
  type PostSearchConversationRequestDto,
  type SearchStreamEvent,
} from "@api/dto/search";
import { getCsrfToken } from "@api/httpClient";
import type { SseEvent } from "@api/sse/parseSseEvents";
import { readSseStream } from "@api/sse/readSseStream";

export const WORKSPACE_SEARCH_CONVERSATIONS_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/search/conversations`;

/** 본문을 읽지 못했을 때 쓰는 값. 상태 코드만으로는 무엇이 잘못됐는지 알 수 없어요 */
const UNKNOWN_REQUEST_ERROR = {
  code: "UNKNOWN",
  message: "답변 생성을 시작하지 못했어요",
};

interface SearchStreamRequestErrorParams {
  status: number;
  code: string;
  message: string;
}

/**
 * accepted 이벤트가 오기 전에 실패한 HTTP 오류예요.
 *
 * accepted 뒤에 오는 `failed` 이벤트와 달리 화면에 보여 줄 답변이 아직 없어서 throw로 알려요.
 */
export class SearchStreamRequestError extends Error {
  /** HTTP 상태 코드. 예: 403, 409 */
  status: number;
  /** 서버 오류 코드. 본문을 읽지 못하면 "UNKNOWN" */
  code: string;

  constructor({ status, code, message }: SearchStreamRequestErrorParams) {
    super(message);

    this.name = "SearchStreamRequestError";
    this.status = status;
    this.code = code;
  }
}

/**
 * fetch는 axios와 달리 baseURL을 모르고 상대 경로도 못 받아서 직접 절대 주소로 만들어요.
 * 배포 환경에서는 API 도메인이 따로 있고, 없으면 지금 보고 있는 오리진을 써요.
 *
 * `??`가 아니라 `||`인 이유는 mock 모드의 `API_BASE_URL`이 없는 값이 아니라 빈 문자열이기 때문이에요.
 */
const toAbsoluteUrl = (path: string) =>
  new URL(path, process.env.API_BASE_URL || window.location.origin).toString();

/** accepted 전 실패 응답의 본문을 읽어 throw할 오류로 바꿔요 */
const toRequestError = async (response: Response) => {
  // 본문이 비었거나 JSON이 아닐 수 있어 읽기 실패는 값 없음으로 다뤄요
  const raw: PostSearchConversationErrorResponseRaw | null = await response
    .json()
    .catch(() => null);

  const { code, message } = raw
    ? new PostSearchConversationErrorResponseDto(raw)
    : UNKNOWN_REQUEST_ERROR;

  return new SearchStreamRequestError({ status: response.status, code, message });
};

/** 명세에 있는 이벤트만 DTO로 감싸요. 모르는 이벤트 이름은 버려요 */
const toSearchStreamEvent = ({
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

/** 첫 질문을 보내고 SSE 응답을 받아요 */
const postSearchConversation = async ({
  workspaceId,
  body,
  signal,
}: CreateSearchConversationApiParams) =>
  fetch(toAbsoluteUrl(WORKSPACE_SEARCH_CONVERSATIONS_API_PATH(workspaceId)), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      "X-XSRF-TOKEN": await getCsrfToken(),
    },
    // 로그인 상태는 쿠키로만 유지되므로 fetch에도 쿠키를 실어 보내요
    credentials: "include",
    body: JSON.stringify(body),
    signal,
  });

/**
 * @description 첫 질문으로 탐색 대화를 시작하고 SSE로 도착하는 답변 이벤트를 순서대로 흘려보내요
 * @param params - 워크스페이스 ID, 첫 질문 전송 요청 본문, 취소용 AbortSignal
 * @returns accepted·progress·delta·evidence·excluded_documents·completed·failed·stopped 이벤트를 순서대로 내는 async generator. 받는 쪽이 중간에 멈추면 스트림 읽기를 정리해요
 * @throws {SearchStreamRequestError} accepted 이벤트 전에 HTTP 오류가 난 경우
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
  const response = await postSearchConversation({ workspaceId, body, signal });

  if (!response.ok || !response.body) throw await toRequestError(response);

  for await (const sseEvent of readSseStream({
    stream: response.body,
    signal,
  })) {
    const searchStreamEvent = toSearchStreamEvent(sseEvent);
    if (searchStreamEvent) yield searchStreamEvent;
  }
}
