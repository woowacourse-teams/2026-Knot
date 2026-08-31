/**
 * 한 턴의 진행 상태.
 *
 * - `pending`: 질문만 있고 답변이 아직 없음
 * - `streaming`: 답변이 조각으로 도착하는 중
 * - `done`: 답변이 끝나 저장됨
 */
export type ChatTurnStatus = "pending" | "streaming" | "done";

/**
 * 화면에 그려지는 대화 단위. 질문 하나와 그에 대한 답변 하나를 묶습니다.
 * `id`는 질문 메시지의 ID입니다.
 */
export interface ChatTurn {
  id: number;
  question: string;
  answer: string | null;
  status: ChatTurnStatus;
}

/**
 * 화면에 그릴 때 근거 문서에 대한 정보까지 붙인 턴.
 * 근거 문서 정보는 메시지 목록과 별개로 오므로 턴을 만든 뒤에 덧붙입니다.
 */
export interface ChatTurnView extends ChatTurn {
  sourceLabel?: string;
}
