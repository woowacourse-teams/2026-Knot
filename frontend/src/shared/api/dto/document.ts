/**
 * 문서 DTO
 *
 * - GET /api/v1/workspaces/{workspaceId}/documents/{documentId}
 */

/** 문서 상태. 확인 대상이 모두 확인하거나 제외되면 ARCHIVED로 바뀌어요 */
export type DocumentStatus = "DRAFT" | "ARCHIVED";

/** 내 확인 상태. 상세·목록 응답이 같은 기준을 써요 */
export type MyConfirmationState = "PENDING" | "CONFIRMED" | "NOT_REQUIRED";

/** 확인 집계의 서버 응답 모양 */
export interface ConfirmationSummaryRaw {
  confirmedCount: number;
  pendingCount: number;
  excludedCount: number;
}

/** 문서 확인 집계. 문서 상세 응답이 담아요 */
export class ConfirmationSummaryDto {
  /** 확인을 마친 대상 수. 확인한 뒤 워크스페이스를 나가도 포함해요 */
  confirmedCount: number;
  /** 아직 확인하지 않은, 현재 활성 멤버인 대상 수 */
  pendingCount: number;
  /** 확인하지 않은 채 워크스페이스를 나가 보관 조건에서 빠진 대상 수 */
  excludedCount: number;

  constructor(raw: ConfirmationSummaryRaw) {
    this.confirmedCount = raw.confirmedCount;
    this.pendingCount = raw.pendingCount;
    this.excludedCount = raw.excludedCount;
  }
}

// GET /api/v1/workspaces/{workspaceId}/documents/{documentId}

/** 문서 상세 조회의 서버 응답 모양 */
export interface GetDocumentResponseRaw {
  id: number;
  recordingSessionId: number;
  topic: string;
  title: string;
  summary: string | null;
  content: string;
  status: DocumentStatus;
  createdAt: string;
  archivedAt: string | null;
  recordingDurationSeconds: number;
  sourceTranscriptId: number;
  myConfirmationState: MyConfirmationState;
  confirmationSummary: ConfirmationSummaryRaw;
}

/** 문서 상세 조회 응답 */
export class GetDocumentResponseDto {
  /** 문서 ID */
  id: number;
  /** 이 문서가 만들어진 녹음 ID. 녹음 API의 `recordingId`와 같은 녹음이에요 */
  recordingSessionId: number;
  /** 폴더 이름. AI가 분류한 주제 (예: "회원") */
  topic: string;
  /** 읽기 전용 제목 */
  title: string;
  /** 한 줄 요약. 없으면 null */
  summary: string | null;
  /** 읽기 전용 Markdown 본문. 정한 문법은 `#` · `##` · `###` · `-` · `**굵게**` */
  content: string;
  /** DRAFT 또는 ARCHIVED. 화면에는 쓰지 않아요 (DOC-R10) */
  status: DocumentStatus;
  /** 문서 생성 시각(ISO 8601, UTC) */
  createdAt: string;
  /** 보관 전환 시각(ISO 8601, UTC). DRAFT면 null */
  archivedAt: string | null;
  /** 일시정지를 뺀 원본 녹음 길이(초). 서버가 소수 초를 버린 정수로 보내요 */
  recordingDurationSeconds: number;
  /** 이 문서를 만든 원문(Transcript) ID */
  sourceTranscriptId: number;
  /** 내 확인 상태. 확인 대상이 아니면 NOT_REQUIRED */
  myConfirmationState: MyConfirmationState;
  /** 확인 집계 */
  confirmationSummary: ConfirmationSummaryDto;

  constructor(raw: GetDocumentResponseRaw) {
    this.id = raw.id;
    this.recordingSessionId = raw.recordingSessionId;
    this.topic = raw.topic;
    this.title = raw.title;
    this.summary = raw.summary;
    this.content = raw.content;
    this.status = raw.status;
    this.createdAt = raw.createdAt;
    this.archivedAt = raw.archivedAt;
    this.recordingDurationSeconds = raw.recordingDurationSeconds;
    this.sourceTranscriptId = raw.sourceTranscriptId;
    this.myConfirmationState = raw.myConfirmationState;
    this.confirmationSummary = new ConfirmationSummaryDto(
      raw.confirmationSummary,
    );
  }
}
