/**
 * @public
 * @category Constants
 * @description 대화 세션에 메시지를 보내는 API 경로를 생성하는 함수
 * @param sessionId - 대화 세션 ID
 * @returns API 경로 문자열
 */
export const SEND_CHAT_MESSAGE_API_PATH = (sessionId: number) =>
  `/api/v1/conversations/${sessionId}/messages`;

/**
 * @public
 * @category Types
 * @interface PostChatMessageApiRequest
 * @description 메시지 전송 요청 타입
 * @property {string} content - 메시지 본문 (최대 10,000자)
 */
export interface PostChatMessageApiRequest {
  content: string;
}

// 응답이 text/event-stream(SseEmitter)이고 swagger에 이벤트 스키마가 없어서, 스트리밍 요청 함수와 mock 핸들러는
// 이벤트 형식이 정해지는 채팅 스트리밍 Issue에서 추가해요. 여기서는 경로와 요청 타입만 정본으로 둬요
