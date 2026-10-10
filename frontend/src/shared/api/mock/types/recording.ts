/** 녹음 세션 상태 */
export type RecordingSessionStatus = "RECORDING" | "PAUSED" | "ENDED";

/** 현재 녹음의 화면용 종합 상태 */
export type CurrentRecordingStatus =
  "RECORDING" | "PAUSED" | "ENDED" | "PROCESSING" | "FAILED";

export interface CurrentRecordingResponse {
  recordingId: number;
  status: CurrentRecordingStatus;
  /** ISO 8601 */
  startedAt: string;
  /** 조회 시점까지 누적 녹음 시간(ms) */
  elapsedMillis: number;
}

export interface RecordingStartResponse {
  recordingId: number;
  status: RecordingSessionStatus;
  /** ISO 8601 */
  startedAt: string;
}

export interface RecordingPauseResponse {
  recordingId: number;
  status: RecordingSessionStatus;
  /** ISO 8601 */
  pausedAt: string;
  /** 일시정지 시점까지 누적 녹음 시간(ms) */
  elapsedMillis: number;
}

export interface RecordingResumeResponse {
  recordingId: number;
  status: RecordingSessionStatus;
  /** ISO 8601 */
  resumedAt: string;
  /** 재개 직전까지 누적 녹음 시간(ms) */
  elapsedMillis: number;
}

export interface RecordingEndResponse {
  recordingId: number;
  status: RecordingSessionStatus;
  /** ISO 8601 */
  endedAt: string;
}

export interface RecordingAudioUploadUrlResponse {
  uploadId: number;
  /** 객체 저장소에 직접 PUT할 절대 URL */
  uploadUrl: string;
  /** ISO 8601 */
  expiresAt: string;
}

/** 최종 오디오 업로드 처리 상태 */
export type RecordingAudioUploadStatus = "RESERVED" | "COMPLETED";

export interface RecordingAudioUploadCompleteResponse {
  recordingId: number;
  uploadId: number;
  uploadStatus: RecordingAudioUploadStatus;
  /** ISO 8601 */
  completedAt: string;
}
