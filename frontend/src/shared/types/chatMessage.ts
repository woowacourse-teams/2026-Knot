export type ChatMessageRole = "USER" | "ASSISTANT";

/** 대화 세션 안의 메시지 한 건 */
export interface ChatMessage {
  id: number;
  role: ChatMessageRole;
  content: string;
  /** 생성 시각 (ISO 8601) */
  createdAt: string;
}
