/** Notion Import 시작 응답 */
export interface NotionImportStartResponse {
  /** Import 실행 ID */
  id: number;
}

/** Notion Import 실행 상태 응답 */
export interface NotionImportStatusResponse {
  /** Import 실행 ID */
  id: number;
  /** Import 진행 상태 */
  status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";
  /** 가져올 전체 Page 수. 아직 알 수 없으면 null */
  totalPageCount: number | null;
  /** 처리한 Page 수 */
  processedPageCount: number;
  /** 사용자 공개용 실패 사유. FAILED가 아니면 null */
  failureReason: string | null;
  /** Import 요청 시각(ISO 8601) */
  createdAt: string;
  /** Import 시작 시각. 시작 전이면 null */
  startedAt: string | null;
  /** Import 종료 시각. 진행 중이면 null */
  completedAt: string | null;
}
