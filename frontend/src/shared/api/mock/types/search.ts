export type SearchStreamStage = "SEARCHING" | "GENERATING";

/** evidence 이벤트의 근거 항목. items[] 모양은 API 명세 미정이라 임시 가정이에요 */
export interface SearchEvidenceItem {
  documentId: number;
  title: string;
  topic: string;
  /** 1~3, 작을수록 관련도가 높아요 */
  rank: number;
}

/** 첫 질문 SSE 스트림 한 번이 흘려보낼 값들. 핸들러가 이 값으로 프레임을 만들어요 */
export interface SearchAnswerStream {
  conversationId: number;
  questionMessageId: number;
  answerMessageId: number;
  /** progress 이벤트로 차례로 보낼 단계 */
  stages: SearchStreamStage[];
  /** delta 이벤트로 차례로 보낼 답변 조각 */
  deltas: string[];
  /** evidence 이벤트의 items. 최대 3개 */
  evidences: SearchEvidenceItem[];
}
