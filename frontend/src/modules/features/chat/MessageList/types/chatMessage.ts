/** 메시지 작성 주체 */
export type ChatMessageRole = "USER" | "ASSISTANT";

/**
 * 대화에 쌓이는 메시지 한 건.
 * 서버의 전체 메시지 조회 응답과 같은 모양이라, API가 붙으면 응답을 그대로 흘려보낼 수 있습니다.
 */
export interface ChatMessage {
  id: number;
  role: ChatMessageRole;
  content: string;
  createdAt: string;
}
