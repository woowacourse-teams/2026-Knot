/**
 * 대화 메시지 DTO
 *
 * - GET  /api/v1/conversations/{sessionId}
 * - POST /api/v1/conversations/{sessionId}/messages (SSE)
 */

/** 메시지 작성 주체. USER는 사용자, ASSISTANT는 AI */
export type ChatMessageRole = "USER" | "ASSISTANT";

/** 대화 메시지의 서버 응답 모양 */
export interface ChatMessageRaw {
  id: number;
  role: ChatMessageRole;
  content: string;
  createdAt: string;
}

/** 대화 메시지. 메시지 목록 조회 응답의 `messages`에 들어감 */
export class ChatMessageDto {
  /** 메시지 ID */
  id: number;
  /** 작성 주체. USER(사용자) 또는 ASSISTANT(AI) */
  role: ChatMessageRole;
  /** 메시지 본문. 최대 10,000자 */
  content: string;
  /** 메시지 작성 시각(ISO 8601) */
  createdAt: string;

  constructor(raw: ChatMessageRaw) {
    this.id = raw.id;
    this.role = raw.role;
    this.content = raw.content;
    this.createdAt = raw.createdAt;
  }
}

// GET /api/v1/conversations/{sessionId}

/** 메시지 목록 조회의 서버 응답 모양. 본문이 배열 하나 */
export type GetChatMessagesResponseRaw = ChatMessageRaw[];

/** 대화 세션의 메시지 목록 조회 응답. 서버 배열을 `messages` 필드에 담음 */
export class GetChatMessagesResponseDto {
  /** 메시지 목록(작성 시각 오름차순). 없으면 빈 배열 */
  messages: ChatMessageDto[];

  constructor(raw: GetChatMessagesResponseRaw) {
    this.messages = raw.map((message) => new ChatMessageDto(message));
  }
}

// POST /api/v1/conversations/{sessionId}/messages (SSE)

/** 메시지 전송 시 앱이 넘기는 값 */
export interface PostChatMessageRequestInput {
  content: string;
}

/** 메시지 전송 요청 본문. 응답은 JSON이 아니라 SSE 스트림 */
export class PostChatMessageRequestDto {
  /** 메시지 내용. 앞뒤 공백 제거, 공백만이면 서버가 거절, 최대 10,000자 */
  content: string;

  constructor({ content }: PostChatMessageRequestInput) {
    this.content = content.trim();
  }
}

/** SSE 연결 전 HTTP 오류의 서버 응답 모양 */
export interface PostChatMessageErrorResponseRaw {
  code: string;
  message: string;
}

/** SSE 연결이 시작되기 전에 실패한 HTTP 오류 응답. 400·401·403·404·409·500이 같은 모양 */
export class PostChatMessageErrorResponseDto {
  /** 오류 코드. 예: VALIDATION_ERROR, CHAT_ACCESS_DENIED, CHAT_STREAM_ALREADY_ACTIVE */
  code: string;
  /** 사용자에게 보여 줄 수 있는 오류 메시지 */
  message: string;

  constructor(raw: PostChatMessageErrorResponseRaw) {
    this.code = raw.code;
    this.message = raw.message;
  }
}

/** `event: chunk`의 data 모양 */
export interface ChatStreamChunkRaw {
  delta: string;
}

/** 답변 조각. 스트림 하나에서 여러 번 도착함 */
export class ChatStreamChunkDto {
  /** LLM 응답의 일부 텍스트. 도착 순서대로 이어 붙이면 전체 답변이 됨 */
  delta: string;

  constructor(raw: ChatStreamChunkRaw) {
    this.delta = raw.delta;
  }
}

/** `event: complete`의 data 모양 */
export interface ChatStreamCompleteRaw {
  messageId: number;
}

/** 답변이 끝나고 저장까지 마쳤음을 알리는 이벤트. 스트림당 한 번만 옴 */
export class ChatStreamCompleteDto {
  /** 저장된 assistant 메시지 ID */
  messageId: number;

  constructor(raw: ChatStreamCompleteRaw) {
    this.messageId = raw.messageId;
  }
}

/** `event: error`의 data 모양 */
export interface ChatStreamErrorRaw {
  code: string;
  message: string;
}

/** 연결이 열린 뒤에 생긴 LLM 오류. HTTP 상태가 아니라 이벤트로 전달됨 */
export class ChatStreamErrorDto {
  /** 오류 코드. LLM_STREAM_FAILED 또는 LLM_STREAM_TIMEOUT */
  code: string;
  /** 사용자에게 보여 줄 수 있는 오류 메시지 */
  message: string;

  constructor(raw: ChatStreamErrorRaw) {
    this.code = raw.code;
    this.message = raw.message;
  }
}

/**
 * 스트림이 흘려보내는 이벤트 한 건.
 *
 * `event`로 갈라 `data`의 모양이 좁혀지도록 유니언으로 둡니다.
 * 스펙에 없는 이벤트 이름은 요청 함수에서 버리므로 여기에 두지 않습니다.
 */
export type ChatStreamEvent =
  | { event: "chunk"; data: ChatStreamChunkDto }
  | { event: "complete"; data: ChatStreamCompleteDto }
  | { event: "error"; data: ChatStreamErrorDto };
