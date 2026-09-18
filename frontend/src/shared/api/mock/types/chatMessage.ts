export type ChatMessageRole = "USER" | "ASSISTANT";

export interface ChatMessage {
  id: number;
  role: ChatMessageRole;
  content: string;
  /** ISO 8601 */
  createdAt: string;
}

/** 메시지 전송 SSE 스트림이 흘려보내는 내용 */
export interface ChatMessageStream {
  /** chunk 이벤트로 나눠 보낼 답변 조각 */
  deltas: string[];
  /** complete 이벤트가 알려 주는 저장된 assistant 메시지 ID */
  messageId: number;
}
