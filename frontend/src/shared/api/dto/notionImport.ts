/**
 * Notion Import(동기화) DTO
 *
 * - POST /api/v1/workspaces/{workspaceId}/imports
 * - GET  /api/v1/imports/{importRunId}
 */

// POST /api/v1/workspaces/{workspaceId}/imports

/** Notion Import 시작의 서버 응답 모양 */
export interface PostNotionImportResponseRaw {
  id: number;
}

/**
 * Notion Import 시작 응답. 요청 본문은 없고 워크스페이스 ID만 경로로 받아요.
 * 새로 시작하면 202, 이미 실행 중인 Import가 있으면 409로 오지만 본문 모양은 같아서
 * 요청 함수가 409도 성공으로 받아 실행 중인 Import를 그대로 추적해요.
 */
export class PostNotionImportResponseDto {
  /** 추적할 Import 실행 ID. `GET /api/v1/imports/{id}`로 상태를 조회해요 */
  id: number;

  constructor(raw: PostNotionImportResponseRaw) {
    this.id = raw.id;
  }
}

// GET /api/v1/imports/{importRunId}

/** Notion Import 실행 상태 조회의 서버 응답 모양 */
export interface GetNotionImportStatusResponseRaw {
  id: number;
  status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";
  totalPageCount: number | null;
  processedPageCount: number;
  failureReason: string | null;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
}

/** Notion Import 실행 상태 조회 응답. 진행 중이면 폴링으로 다시 조회해요 */
export class GetNotionImportStatusResponseDto {
  /** Import 실행 ID */
  id: number;
  /** Import 진행 상태. PENDING·RUNNING은 진행 중, COMPLETED·FAILED는 끝난 상태예요 */
  status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";
  /** 가져올 전체 Page 수. 아직 알 수 없으면 null */
  totalPageCount: number | null;
  /** 처리한 Page 수. 완료되면 새로 들어온 문서 수 안내에 써요 */
  processedPageCount: number;
  /** 사용자에게 보여줄 실패 사유. FAILED가 아니면 null */
  failureReason: string | null;
  /** Import 요청 시각(ISO 8601) */
  createdAt: string;
  /** Import 시작 시각(ISO 8601). 시작 전이면 null */
  startedAt: string | null;
  /** Import 종료 시각(ISO 8601). 진행 중이면 null */
  completedAt: string | null;

  constructor(raw: GetNotionImportStatusResponseRaw) {
    this.id = raw.id;
    this.status = raw.status;
    this.totalPageCount = raw.totalPageCount;
    this.processedPageCount = raw.processedPageCount;
    this.failureReason = raw.failureReason;
    this.createdAt = raw.createdAt;
    this.startedAt = raw.startedAt;
    this.completedAt = raw.completedAt;
  }
}
