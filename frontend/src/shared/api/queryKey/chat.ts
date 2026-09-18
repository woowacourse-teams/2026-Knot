/**
 * 채팅 쿼리 키
 *
 * - `sessions` 워크스페이스의 대화 세션 목록
 * - `messages` 한 세션의 메시지 이력
 */
export const chatKeys = {
  all: ["chat"] as const,

  sessions: (workspaceId: number | null) =>
    [...chatKeys.all, "sessions", workspaceId] as const,

  messages: (sessionId: number | null) =>
    [...chatKeys.all, "messages", sessionId] as const,
};
