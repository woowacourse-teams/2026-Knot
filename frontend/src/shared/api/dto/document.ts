/**
 * 문서 DTO
 *
 * - GET /api/v1/workspaces/{workspaceId}/documents
 * - GET /api/v1/workspaces/{workspaceId}/documents/{documentId}
 * - GET /api/v1/workspaces/{workspaceId}/documents/{documentId}/confirmations
 * - PUT /api/v1/workspaces/{workspaceId}/documents/{documentId}/confirmations/me
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

/** 문서 확인 집계. 문서 목록 · 문서 상세 · 확인 대상 조회 · 내 확인 응답이 담아요 */
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

// GET /api/v1/workspaces/{workspaceId}/documents

/** 주제 폴더 하나의 서버 응답 모양 */
export interface DocumentTopicRaw {
  topic: string;
  documentCount: number;
}

/** 문서가 있는 주제 폴더 하나. 문서 목록 조회 응답의 `topics`가 담아요 */
export class DocumentTopicDto {
  /** 폴더 이름. AI가 분류한 주제 (예: "회원") */
  topic: string;
  /**
   * 조회 조건(녹음 ID 등)을 만족하는 이 주제의 문서 수.
   * 한 응답에 담긴 문서 수가 아니라 cursor · size와 관계없이 센 값이에요. 조건이 없으면 워크스페이스 전체를 센 값이에요
   */
  documentCount: number;

  constructor(raw: DocumentTopicRaw) {
    this.topic = raw.topic;
    this.documentCount = raw.documentCount;
  }
}

/** 문서 목록 항목의 서버 응답 모양 */
export interface DocumentListItemRaw {
  id: number;
  recordingSessionId: number;
  topic: string;
  title: string;
  summary: string | null;
  status: DocumentStatus;
  createdAt: string;
  recordingDurationSeconds: number;
  myConfirmationState: MyConfirmationState;
  confirmationSummary: ConfirmationSummaryRaw;
}

/** 문서 목록의 문서 하나. 문서 상세에서 본문 · 보관 시각 · 원문 ID가 빠진 모양이에요 */
export class DocumentListItemDto {
  /** 문서 ID */
  id: number;
  /** 이 문서가 만들어진 녹음 ID. 녹음 API의 `recordingId`와 같은 녹음이에요 */
  recordingSessionId: number;
  /** 폴더 이름. 응답의 `topics` 중 하나와 같아요 */
  topic: string;
  /** 읽기 전용 제목 */
  title: string;
  /** 한 줄 요약. 없으면 null */
  summary: string | null;
  /** DRAFT 또는 ARCHIVED. 화면에는 쓰지 않아요 (DOC-R10) */
  status: DocumentStatus;
  /** 문서 생성 시각(ISO 8601, UTC) */
  createdAt: string;
  /** 일시정지를 뺀 원본 녹음 길이(초). 서버가 소수 초를 버린 정수로 보내요 */
  recordingDurationSeconds: number;
  /** 내 확인 상태. 확인 대상이 아니면 NOT_REQUIRED */
  myConfirmationState: MyConfirmationState;
  /** 확인 집계 */
  confirmationSummary: ConfirmationSummaryDto;

  constructor(raw: DocumentListItemRaw) {
    this.id = raw.id;
    this.recordingSessionId = raw.recordingSessionId;
    this.topic = raw.topic;
    this.title = raw.title;
    this.summary = raw.summary;
    this.status = raw.status;
    this.createdAt = raw.createdAt;
    this.recordingDurationSeconds = raw.recordingDurationSeconds;
    this.myConfirmationState = raw.myConfirmationState;
    this.confirmationSummary = new ConfirmationSummaryDto(
      raw.confirmationSummary,
    );
  }
}

/** 문서 목록 조회의 서버 응답 모양 */
export interface GetDocumentsResponseRaw {
  topics: DocumentTopicRaw[];
  items: DocumentListItemRaw[];
  nextCursor: string | null;
}

/**
 * 문서 목록 조회 응답.
 *
 * 문서(`items`)만 한 페이지씩 나눠서 오고, 주제 폴더(`topics`)는 어느 페이지에서나 전체가 와요.
 * 녹음 ID 같은 조회 조건은 `items`와 `topics` 모두에 적용돼요.
 */
export class GetDocumentsResponseDto {
  /** 조회 조건을 만족하는 문서가 있는 주제 폴더 전체. 조건에 맞는 문서가 없는 주제는 들어 있지 않아요. 순서는 명세에 없어요 */
  topics: DocumentTopicDto[];
  /** 문서 한 페이지. 최신순(생성 시각 내림차순, 같으면 ID 내림차순)이에요 */
  items: DocumentListItemDto[];
  /** 다음 페이지 커서. 마지막 페이지면 null */
  nextCursor: string | null;

  constructor(raw: GetDocumentsResponseRaw) {
    this.topics = raw.topics.map((topic) => new DocumentTopicDto(topic));
    this.items = raw.items.map((item) => new DocumentListItemDto(item));
    this.nextCursor = raw.nextCursor;
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

// GET /api/v1/workspaces/{workspaceId}/documents/{documentId}/confirmations

/** 확인 대상 한 명의 상태. EXCLUDED는 확인하지 않은 채 워크스페이스를 나간 대상이에요 */
export type DocumentConfirmationState = "CONFIRMED" | "PENDING" | "EXCLUDED";

/** 확인 대상 한 명의 서버 응답 모양 */
export interface DocumentConfirmationItemRaw {
  memberId: number;
  nickname: string;
  profileImageUrl: string | null;
  confirmedAt: string | null;
  state: DocumentConfirmationState;
}

/** 문서의 확인 대상 한 명. 확인 대상 조회 응답의 `items`가 담아요 */
export class DocumentConfirmationItemDto {
  /** 대상 멤버 ID */
  memberId: number;
  /** 대상의 지금 닉네임 */
  nickname: string;
  /** 대상의 지금 프로필 이미지(절대 URL). 없으면 null */
  profileImageUrl: string | null;
  /** 처음 확인한 시각(ISO 8601, UTC). 확인하지 않았으면 null */
  confirmedAt: string | null;
  /** CONFIRMED · PENDING · EXCLUDED. 확인한 뒤 나간 대상은 CONFIRMED로 남고, EXCLUDED는 확인하지 않은 채 나간 대상이에요 */
  state: DocumentConfirmationState;

  constructor(raw: DocumentConfirmationItemRaw) {
    this.memberId = raw.memberId;
    this.nickname = raw.nickname;
    this.profileImageUrl = raw.profileImageUrl;
    this.confirmedAt = raw.confirmedAt;
    this.state = raw.state;
  }
}

/**
 * 확인 대상 조회의 서버 응답 모양.
 *
 * `confirmedByMe`는 확인 대상이 아닐 때와 아직 확인하지 않았을 때가 모두 false라 둘을 구분하지 못해요.
 */
export interface GetDocumentConfirmationsResponseRaw {
  documentId: number;
  confirmedCount: number;
  pendingCount: number;
  excludedCount: number;
  confirmedByMe: boolean;
  items: DocumentConfirmationItemRaw[];
  nextCursor: string | null;
}

/** 문서의 확인 대상과 진행 현황 조회 응답 */
export class GetDocumentConfirmationsResponseDto {
  /** 문서 ID */
  documentId: number;
  /** 확인 집계. 서버가 따로 준 세 값(confirmedCount · pendingCount · excludedCount)을 문서 상세와 같은 모양으로 묶었어요 */
  confirmationSummary: ConfirmationSummaryDto;
  /** 문서가 만들어질 때 정해진 확인 대상. 한 번에 최대 100명 */
  items: DocumentConfirmationItemDto[];
  /** 다음 페이지 커서. 마지막 페이지면 null */
  nextCursor: string | null;

  // 내 확인 상태는 문서 상세의 myConfirmationState를 쓰므로 confirmedByMe는 옮기지 않아요
  constructor(raw: GetDocumentConfirmationsResponseRaw) {
    this.documentId = raw.documentId;
    this.confirmationSummary = new ConfirmationSummaryDto(raw);
    this.items = raw.items.map((item) => new DocumentConfirmationItemDto(item));
    this.nextCursor = raw.nextCursor;
  }
}

// PUT /api/v1/workspaces/{workspaceId}/documents/{documentId}/confirmations/me

/** 내 문서 확인의 서버 응답 모양 */
export interface PutDocumentConfirmationResponseRaw {
  documentId: number;
  confirmedAt: string;
  documentStatus: DocumentStatus;
  archivedAt: string | null;
  confirmationSummary: ConfirmationSummaryRaw;
}

/**
 * 내 문서 확인 응답. 요청 본문은 없고, 로그인한 멤버가 확인한 것으로 기록돼요.
 *
 * 이미 확인한 문서에 다시 요청해도 같은 결과(200)를 돌려줘요. 확인은 취소할 수 없어요.
 */
export class PutDocumentConfirmationResponseDto {
  /** 확인한 문서 ID */
  documentId: number;
  /** 처음 확인한 시각(ISO 8601, UTC). 다시 요청해도 바뀌지 않아요 */
  confirmedAt: string;
  /** 확인 뒤의 문서 상태. 이 확인으로 남은 대상이 없어지면 ARCHIVED */
  documentStatus: DocumentStatus;
  /** 보관 전환 시각(ISO 8601, UTC). DRAFT면 null */
  archivedAt: string | null;
  /** 확인 뒤의 확인 집계. 내 확인 상태(myConfirmationState)는 들어 있지 않아요 */
  confirmationSummary: ConfirmationSummaryDto;

  constructor(raw: PutDocumentConfirmationResponseRaw) {
    this.documentId = raw.documentId;
    this.confirmedAt = raw.confirmedAt;
    this.documentStatus = raw.documentStatus;
    this.archivedAt = raw.archivedAt;
    this.confirmationSummary = new ConfirmationSummaryDto(
      raw.confirmationSummary,
    );
  }
}
