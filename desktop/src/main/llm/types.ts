/** 사용자 LLM 클라이언트가 공통으로 받는 요청 모양. provider별 본문 조립은 각 클라이언트가 한다 */

export interface LlmChatMessage {
  role: "user" | "assistant";
  content: string;
}

export interface LlmStreamRequest {
  /** 규칙 문장 + 근거 블록. 비어 있으면 system을 보내지 않는다 */
  system: string;
  /** 세션 이력. 같은 역할이 연속되지 않고 마지막은 `user`다(`chat/prompt.ts`) */
  messages: LlmChatMessage[];
}

export interface LlmStreamOptions {
  baseUrl: string;
  model: string;
  /** 없으면 null. `openai-compatible`은 없어도 호출하고 `anthropic`은 거부한다 */
  apiKey: string | null;
  signal: AbortSignal;
  /** 테스트 주입용. 기본은 전역 fetch(Node undici) */
  fetch?: typeof fetch;
}
