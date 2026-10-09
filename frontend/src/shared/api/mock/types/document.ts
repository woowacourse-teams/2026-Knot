export interface DocumentConfirmationSummary {
  confirmedCount: number;
  pendingCount: number;
  excludedCount: number;
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
