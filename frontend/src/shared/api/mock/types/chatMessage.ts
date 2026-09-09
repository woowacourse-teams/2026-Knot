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

/** 검색 출처가 가리키는 원본 Notion 페이지 */
export interface SearchReferencePage {
  /** 원본 Notion 페이지 ID(UUID 문자열) */
  id: string;
  title: string;
  notionUrl: string;
  /** ISO 8601 */
  createdAt: string;
  /** ISO 8601 */
  updatedAt: string;
}

/** 답변 메시지의 검색 출처 한 건(청크 단위) */
export interface SearchReference {
  id: number;
  messageId: number;
  /** 1부터, 최대 8 */
  rank: number;
  /** 0~1 */
  relevanceScore: number;
  source: "NOTION";
  notionPage: SearchReferencePage;
}

export interface ChatMessageSourcesResponse {
  searchReferences: SearchReference[];
}
