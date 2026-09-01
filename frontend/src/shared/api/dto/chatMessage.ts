/**
 * 대화 메시지 DTO
 *
 * - GET /api/v1/conversations/{sessionId}
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
