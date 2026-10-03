export type ChatMessageRole = "USER" | "ASSISTANT";

export interface ChatMessage {
  id: number;
  role: ChatMessageRole;
  content: string;
  /** ISO 8601 */
  createdAt: string;
}
