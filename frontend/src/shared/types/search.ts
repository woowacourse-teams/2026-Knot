// v2 메시지 조회 API(GET /api/v1/workspaces/{workspaceId}/search/conversations/{conversationId}/messages)의
// items[] 필드 이름을 그대로 따라요. API를 연결하면 shared/api/dto의 응답 클래스로 대체될 임시 자리예요.

export type SearchMessageRole = "USER" | "ASSISTANT";

export type SearchMessageStatus =
  "RECEIVED" | "STREAMING" | "COMPLETED" | "FAILED" | "STOPPED";

/** 답변의 근거가 된 문서 */
export interface SearchEvidence {
  documentId: number;
  title: string;
  topic: string;
  /** 출처(근거가 나온 곳의 종류, v2는 `문서`만). API에 아직 없음 — BE에 요청 예정, mock으로만 채움 */
  sourceType: string;
  /** 문서 날짜(ISO). API에 아직 없음 — BE에 요청 예정, mock으로만 채움 */
  createdAt: string;
  /** 관련도 순위. 1~3이고 작을수록 관련도가 높아요. */
  rank: number;
}

/** 탐색 대화의 메시지 하나 */
export interface SearchMessage {
  id: number;
  role: SearchMessageRole;
  sequence: number;
  content: string;
  status: SearchMessageStatus;
  createdAt: string;
  /** 답변의 근거 문서. 질문(`USER`)은 빈 배열이에요. */
  evidences: SearchEvidence[];
}
