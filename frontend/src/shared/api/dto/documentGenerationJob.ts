/**
 * 문서 생성 작업 DTO
 *
 * - POST /api/v1/workspaces/{workspaceId}/document-generation-jobs/{jobId}/retry
 */

/** 문서 생성 작업의 상태 */
export type DocumentGenerationJobStatus =
  "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";

// POST /api/v1/workspaces/{workspaceId}/document-generation-jobs/{jobId}/retry

/** 문서 생성 작업 재시도의 서버 응답 모양 */
export interface PostDocumentGenerationJobRetryResponseRaw {
  jobId: number;
  status: DocumentGenerationJobStatus;
  attemptCount: number;
}

/**
 * 문서 생성 작업 재시도 응답(202). 요청 본문은 없어요.
 * 실패한 작업을 새로 만들지 않고, 같은 작업을 다시 대기 상태로 돌려요.
 */
export class PostDocumentGenerationJobRetryResponseDto {
  /** 다시 접수한 문서 생성 작업 ID. 요청한 작업과 같아요 */
  jobId: number;
  /** 접수한 뒤의 상태. 항상 `QUEUED` */
  status: DocumentGenerationJobStatus;
  /** 접수한 뒤의 누적 시도 수(1 이상). 처음 실행 · 사용자 재시도 · 서버 자동 재시도를 모두 세요. 사용자 재시도 한도(3번)를 판단하는 값이 아니에요 */
  attemptCount: number;

  constructor(raw: PostDocumentGenerationJobRetryResponseRaw) {
    this.jobId = raw.jobId;
    this.status = raw.status;
    this.attemptCount = raw.attemptCount;
  }
}
