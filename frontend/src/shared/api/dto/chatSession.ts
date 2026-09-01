/**
 * 대화 세션 DTO
 *
 * - GET  /api/v1/workspaces/{workspaceId}/conversations
 * - POST /api/v1/workspaces/{workspaceId}/conversations
 */

/** 대화 세션의 서버 응답 모양 */
export interface ChatSessionRaw {
  id: number;
  title: string;
  createdAt: string;
  lastMessageAt: string;
}

/** 대화 세션. 세션 목록 조회·생성 응답이 공유 */
export class ChatSessionDto {
  /** 대화 세션 ID */
  id: number;
  /** 세션 제목. 최대 255자 */
  title: string;
  /** 세션 생성 시각(ISO 8601) */
  createdAt: string;
  /** 마지막 메시지 시각(ISO 8601). 메시지가 없으면 생성 시각과 같음 */
  lastMessageAt: string;

  constructor(raw: ChatSessionRaw) {
    this.id = raw.id;
    this.title = raw.title;
    this.createdAt = raw.createdAt;
    this.lastMessageAt = raw.lastMessageAt;
  }
}

// GET /api/v1/workspaces/{workspaceId}/conversations

/** 대화 세션 목록 조회의 서버 응답 모양. 본문이 배열 하나 */
export type GetChatSessionsResponseRaw = ChatSessionRaw[];

/** 워크스페이스의 대화 세션 목록 조회 응답. 서버 배열을 `sessions` 필드에 담음 */
export class GetChatSessionsResponseDto {
  /** 대화 세션 목록. 없으면 빈 배열 */
  sessions: ChatSessionDto[];

  constructor(raw: GetChatSessionsResponseRaw) {
    this.sessions = raw.map((session) => new ChatSessionDto(session));
  }
}

// POST /api/v1/workspaces/{workspaceId}/conversations

/** 대화 세션 생성 시 앱이 넘기는 값 */
export interface PostChatSessionRequestInput {
  title?: string;
}

/** 대화 세션 생성 요청 본문 */
export class PostChatSessionRequestDto {
  /** 세션 제목. 최대 255자. 생략하면 서버가 기본 제목을 붙임 */
  title?: string;

  constructor({ title }: PostChatSessionRequestInput) {
    this.title = title;
  }
}

/** 대화 세션 생성의 서버 응답 모양 */
export interface PostChatSessionResponseRaw {
  id: number;
  title: string;
  createdAt: string;
  lastMessageAt: string;
}

/** 대화 세션 생성 응답 */
export class PostChatSessionResponseDto {
  /** 생성된 대화 세션 ID */
  id: number;
  /** 세션 제목. 최대 255자 */
  title: string;
  /** 세션 생성 시각(ISO 8601) */
  createdAt: string;
  /** 마지막 메시지 시각(ISO 8601). 생성 직후라 생성 시각과 같음 */
  lastMessageAt: string;

  constructor(raw: PostChatSessionResponseRaw) {
    const session = new ChatSessionDto(raw);

    this.id = session.id;
    this.title = session.title;
    this.createdAt = session.createdAt;
    this.lastMessageAt = session.lastMessageAt;
  }
}
