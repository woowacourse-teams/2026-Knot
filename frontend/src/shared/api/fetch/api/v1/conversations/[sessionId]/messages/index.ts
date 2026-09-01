export const SEND_CHAT_MESSAGE_API_PATH = (sessionId: number) =>
  `/api/v1/conversations/${sessionId}/messages`;

// 요청 본문은 `content`(최대 10,000자) 하나예요
// 응답이 SSE(text/event-stream)라 요청 타입·요청 함수·mock 핸들러는 이벤트 형식이 정해지는 채팅 스트리밍 Issue에서 추가해요
