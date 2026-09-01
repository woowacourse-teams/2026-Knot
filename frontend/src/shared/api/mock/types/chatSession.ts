/** 목록 조회와 생성 응답이 같은 모양을 써요 */
export interface ChatSession {
  id: number;
  title: string;
  /** ISO 8601 */
  createdAt: string;
  /** ISO 8601 */
  lastMessageAt: string;
}
