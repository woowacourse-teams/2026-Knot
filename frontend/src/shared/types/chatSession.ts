/** 워크스페이스 안의 대화 세션. 목록 조회와 생성 응답이 같은 모양을 써요 */
export interface ChatSession {
  id: number;
  title: string;
  /** 생성 시각 (ISO 8601) */
  createdAt: string;
  /** 마지막 메시지 시각 (ISO 8601) */
  lastMessageAt: string;
}
