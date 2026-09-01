import {
  ChatStreamChunkDto,
  ChatStreamCompleteDto,
  ChatStreamErrorDto,
  PostChatMessageErrorResponseDto,
  type ChatStreamEvent,
  type PostChatMessageErrorResponseRaw,
  type PostChatMessageRequestDto,
} from "@api/dto/chatMessage";
import { getCsrfToken } from "@api/httpClient";
import { parseSseEvents, type SseEvent } from "@api/sse/parseSseEvents";

export const SEND_CHAT_MESSAGE_API_PATH = (sessionId: number) =>
  `/api/v1/conversations/${sessionId}/messages`;

/** 본문을 읽지 못했을 때 쓰는 값. 상태 코드만으로는 무엇이 잘못됐는지 알 수 없어요 */
const UNKNOWN_REQUEST_ERROR = {
  code: "UNKNOWN",
  message: "답변 생성을 시작하지 못했어요",
};

interface ChatStreamRequestErrorParams {
  status: number;
  code: string;
  message: string;
}

/**
 * SSE 연결이 열리기 전에 실패한 HTTP 오류.
 *
 * 연결 뒤에 오는 `error` 이벤트와 달리 화면에 보여 줄 답변이 아직 없으므로 throw로 알립니다.
 */
export class ChatStreamRequestError extends Error {
  /** HTTP 상태 코드. 400·401·403·404·409·500 */
  status: number;
  /** 서버 오류 코드. 본문을 읽지 못하면 "UNKNOWN" */
  code: string;

  constructor({ status, code, message }: ChatStreamRequestErrorParams) {
    super(message);

    this.name = "ChatStreamRequestError";
    this.status = status;
    this.code = code;
  }
}

/**
 * fetch는 axios와 달리 baseURL을 모르고 상대 경로도 못 받으므로 직접 절대 주소로 만듭니다.
 * 배포 환경에서는 API 도메인이 따로 있고, 없으면 지금 보고 있는 오리진을 씁니다.
 */
const toAbsoluteUrl = (path: string) =>
  new URL(path, process.env.API_BASE_URL ?? window.location.origin).toString();

/** 연결 전 실패 응답의 본문을 읽어 throw할 오류로 바꿉니다 */
const toRequestError = async (response: Response) => {
  // 본문이 비었거나 JSON이 아닐 수 있어 읽기 실패는 값 없음으로 다룹니다
  const raw: PostChatMessageErrorResponseRaw | null = await response
    .json()
    .catch(() => null);

  const { code, message } = raw
    ? new PostChatMessageErrorResponseDto(raw)
    : UNKNOWN_REQUEST_ERROR;

  return new ChatStreamRequestError({ status: response.status, code, message });
};

/** 스펙에 있는 이벤트만 DTO로 감쌉니다. 모르는 이벤트 이름은 버립니다 */
const toChatStreamEvent = ({
  event,
  data,
}: SseEvent): ChatStreamEvent | null => {
  switch (event) {
    case "chunk":
      return { event, data: new ChatStreamChunkDto(JSON.parse(data)) };
    case "complete":
      return { event, data: new ChatStreamCompleteDto(JSON.parse(data)) };
    case "error":
      return { event, data: new ChatStreamErrorDto(JSON.parse(data)) };
    default:
      return null;
  }
};

interface StreamChatMessageApiParams {
  sessionId: number;
  body: PostChatMessageRequestDto;
  signal?: AbortSignal;
}

/**
 * @description 대화 세션에 메시지를 보내고 SSE로 도착하는 답변을 순서대로 흘려보냅니다
 * @param params - 대화 세션 ID, 메시지 전송 요청 본문, 취소용 AbortSignal
 * @returns chunk·complete·error 이벤트를 순서대로 내는 async generator
 * @throws {ChatStreamRequestError} SSE 연결이 열리기 전에 HTTP 오류가 난 경우
 * @example
 * for await (const { event, data } of streamChatMessageApi({ sessionId: 100, body })) {
 *   if (event === "chunk") answer += data.delta;
 * }
 */
export async function* streamChatMessageApi({
  sessionId,
  body,
  signal,
}: StreamChatMessageApiParams) {
  const response = await fetch(
    toAbsoluteUrl(SEND_CHAT_MESSAGE_API_PATH(sessionId)),
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream",
        "X-XSRF-TOKEN": await getCsrfToken(),
      },
      // 로그인 상태는 쿠키로만 유지되므로 fetch에도 쿠키를 실어 보냅니다
      credentials: "include",
      body: JSON.stringify(body),
      signal,
    },
  );

  if (!response.ok || !response.body) throw await toRequestError(response);

  const reader = response.body.getReader();
  // stream: true여야 한글처럼 여러 바이트인 글자가 청크 경계에서 깨지지 않습니다
  const decoder = new TextDecoder();
  let buffer = "";

  /** 버퍼에 이어 붙여 완성된 이벤트만 내보냅니다 */
  function* drain(chunk: string) {
    const { events, buffer: rest } = parseSseEvents({ buffer, chunk });
    buffer = rest;

    for (const event of events) {
      if (signal?.aborted) return; // 취소한 뒤에는 남은 이벤트도 내지 않습니다

      const streamEvent = toChatStreamEvent(event);
      if (streamEvent) yield streamEvent;
    }
  }

  try {
    // 소비자가 이벤트를 받다가 취소할 수 있으므로 매번 다시 확인합니다
    while (!signal?.aborted) {
      const { done, value } = await reader.read();

      if (done) {
        // 마지막 프레임이 빈 줄 없이 끝났을 수 있어 남은 버퍼를 한 번 더 비웁니다
        yield* drain("\n\n");
        break;
      }

      yield* drain(decoder.decode(value, { stream: true }));
    }
  } catch (error) {
    // 취소는 사용자가 그만둔 것이라 실패로 보고하지 않고 조용히 끝냅니다
    if (!signal?.aborted) throw error;
  } finally {
    // 소비자가 중간에 멈춰도 연결을 놓아주도록 리더를 닫습니다.
    // 취소된 스트림은 cancel()이 끝나지 않을 수 있어 기다리지 않고 실패도 무시합니다
    void reader.cancel().catch(() => undefined);
  }
}
