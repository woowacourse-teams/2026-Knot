export interface DocumentConfirmationSummary {
  confirmedCount: number;
  pendingCount: number;
  excludedCount: number;
}

export interface DocumentTopic {
  topic: string;
  /** 한 응답에 담긴 문서 수가 아니라 그 주제의 전체 문서 수 */
  documentCount: number;
}

export interface DocumentListItem {
  id: number;
  recordingSessionId: number;
  topic: string;
  title: string;
  /** 없으면 null */
  summary: string | null;
  status: "DRAFT" | "ARCHIVED";
  createdAt: string;
  recordingDurationSeconds: number;
  myConfirmationState: "PENDING" | "CONFIRMED" | "NOT_REQUIRED";
  confirmationSummary: DocumentConfirmationSummary;
}

export interface DocumentsResponse {
  /** 페이지와 관계없이 문서가 있는 주제 전체 */
  topics: DocumentTopic[];
  items: DocumentListItem[];
  /** 마지막 페이지면 null */
  nextCursor: string | null;
}

export interface DocumentDetailResponse {
  id: number;
  recordingSessionId: number;
  topic: string;
  title: string;
  /** 없으면 null */
  summary: string | null;
  content: string;
  status: "DRAFT" | "ARCHIVED";
  createdAt: string;
  /** DRAFT면 null */
  archivedAt: string | null;
  recordingDurationSeconds: number;
  sourceTranscriptId: number;
  myConfirmationState: "PENDING" | "CONFIRMED" | "NOT_REQUIRED";
  confirmationSummary: DocumentConfirmationSummary;
}

export interface DocumentConfirmationItem {
  memberId: number;
  nickname: string;
  /** 없으면 null */
  profileImageUrl: string | null;
  /** 확인하지 않았으면 null */
  confirmedAt: string | null;
  state: "CONFIRMED" | "PENDING" | "EXCLUDED";
}

export interface DocumentConfirmationsResponse {
  documentId: number;
  confirmedCount: number;
  pendingCount: number;
  excludedCount: number;
  confirmedByMe: boolean;
  items: DocumentConfirmationItem[];
  /** 마지막 페이지면 null */
  nextCursor: string | null;
}

export interface DocumentMyConfirmationResponse {
  documentId: number;
  confirmedAt: string;
  documentStatus: "DRAFT" | "ARCHIVED";
  /** DRAFT면 null */
  archivedAt: string | null;
  confirmationSummary: DocumentConfirmationSummary;
}
