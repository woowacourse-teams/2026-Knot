/**
 * 녹음 DTO
 *
 * - POST /api/v1/workspaces/{workspaceId}/recordings
 * - GET /api/v1/workspaces/{workspaceId}/recordings/{recordingId}
 * - POST /api/v1/workspaces/{workspaceId}/recordings/{recordingId}/pause
 * - POST /api/v1/workspaces/{workspaceId}/recordings/{recordingId}/resume
 * - POST /api/v1/workspaces/{workspaceId}/recordings/{recordingId}/end
 * - POST /api/v1/workspaces/{workspaceId}/recordings/{recordingId}/audio-upload-url
 * - POST /api/v1/workspaces/{workspaceId}/recordings/{recordingId}/audio-upload-complete
 */

/** 녹음 세션 상태. 시작·일시정지·재개·종료 응답과 녹음 상세 조회의 `sessionStatus`가 공유하는 서버 값 */
export type RecordingSessionStatus = "RECORDING" | "PAUSED" | "ENDED";

// POST /api/v1/workspaces/{workspaceId}/recordings

/** 녹음 시작 시 앱이 넘기는 값. 모두 이 탭이 만들어 sessionStorage에 보관한 값 */
export interface PostRecordingRequestInput {
  requestId: string;
  tabId: string;
  controlToken: string;
}

/** 녹음 시작 요청 본문 */
export class PostRecordingRequestDto {
  /** 같은 시작 요청을 다시 보낼 때 유지하는 UUID. 같은 값이면 서버가 새 세션을 만들지 않고 기존 세션을 돌려줘요 */
  requestId: string;
  /** 녹음을 시작한 최초 탭의 UUID. 새로고침해도 유지해요 */
  tabId: string;
  /** 32바이트 난수를 패딩 없는 Base64URL로 쓴 43자 제어 증명. 일시정지·재개에 그대로 다시 보내요 */
  controlToken: string;

  constructor({ requestId, tabId, controlToken }: PostRecordingRequestInput) {
    this.requestId = requestId;
    this.tabId = tabId;
    this.controlToken = controlToken;
  }
}

/** 녹음 시작의 서버 응답 모양 */
export interface PostRecordingResponseRaw {
  recordingId: number;
  status: RecordingSessionStatus;
  startedAt: string;
}

/** 녹음 시작 응답. 새 세션은 201, 같은 requestId 재시도는 기존 세션을 200으로 같은 모양에 담아요 */
export class PostRecordingResponseDto {
  /** 녹음 세션 ID */
  recordingId: number;
  /** 새 요청은 `RECORDING`, 재시도는 기존 세션의 현재 상태 */
  status: RecordingSessionStatus;
  /** 서버가 확정한 녹음 시작 시각(ISO 8601) */
  startedAt: string;

  constructor(raw: PostRecordingResponseRaw) {
    this.recordingId = raw.recordingId;
    this.status = raw.status;
    this.startedAt = raw.startedAt;
  }
}

// GET /api/v1/workspaces/{workspaceId}/recordings/{recordingId}

/**
 * 녹음 하나의 화면용 종합 상태. 녹음 세션 상태에 업로드·전사·문서 생성 결과를 합친 값이에요.
 *
 * - `ENDED`: 녹음을 끝냈고 최종 오디오 업로드 확인 전
 * - `PROCESSING`: 전사나 문서 생성이 대기 또는 진행 중
 * - `COMPLETED`: 문서 생성 완료
 * - `NO_CONTENT`: 전사 결과에 문서로 정리할 내용이 없음
 * - `FAILED`: 업로드·전사·문서 생성 중 한 단계가 실패로 확정됨
 */
export type RecordingStatus =
  | "RECORDING"
  | "PAUSED"
  | "ENDED"
  | "PROCESSING"
  | "COMPLETED"
  | "NO_CONTENT"
  | "FAILED";

/** 녹음 상세가 알려 주는 최종 오디오 업로드 단계의 상태. 업로드 완료 확인 응답의 `RecordingAudioUploadStatus`와는 다른 값이에요 */
export type RecordingAudioUploadStepStatus =
  "NOT_STARTED" | "PENDING" | "COMPLETED" | "FAILED";

/** 전사·문서 생성 작업의 상태 */
export type RecordingJobStatus =
  "NOT_STARTED" | "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";

/** 실패한 단계 */
export type RecordingFailureStage =
  "AUDIO_UPLOAD" | "TRANSCRIPTION" | "DOCUMENT_GENERATION";

/** 녹음 상세 조회의 서버 응답 모양 */
export interface GetRecordingResponseRaw {
  recordingId: number;
  status: RecordingStatus;
  sessionStatus: RecordingSessionStatus;
  audioUploadStatus: RecordingAudioUploadStepStatus;
  startedAt: string;
  endedAt: string | null;
  durationMillis: number | null;
  transcriptionStatus: RecordingJobStatus;
  documentGenerationStatus: RecordingJobStatus;
  documentGenerationJobId: number | null;
  failureStage: RecordingFailureStage | null;
  failureReason: string | null;
}

/** 녹음 상세 조회 응답. 녹음을 끝낸 뒤 이 응답을 다시 조회해 문서 정리가 어디까지 됐는지 확인해요 */
export class GetRecordingResponseDto {
  /** 녹음 세션 ID */
  recordingId: number;
  /** 화면용 종합 상태. 정리 화면은 이 값으로 무엇을 보여 줄지 정해요 */
  status: RecordingStatus;
  /** 녹음 세션 상태. 전사·문서 생성이 실패해도 `ENDED`로 남아요 */
  sessionStatus: RecordingSessionStatus;
  /** 최종 오디오 업로드 단계의 상태 */
  audioUploadStatus: RecordingAudioUploadStepStatus;
  /** 녹음 시작 시각(ISO 8601) */
  startedAt: string;
  /** 녹음 종료 시각(ISO 8601). 끝나기 전이면 null */
  endedAt: string | null;
  /** 녹음 길이(ms). 끝나기 전이면 null */
  durationMillis: number | null;
  /** 전사 작업 상태 */
  transcriptionStatus: RecordingJobStatus;
  /** 문서 생성 작업 상태 */
  documentGenerationStatus: RecordingJobStatus;
  /** 문서 생성 작업 ID. 문서 만들기 재시도에 넘겨요. 작업이 만들어지기 전이거나 전사 실패로 작업이 없으면 null */
  documentGenerationJobId: number | null;
  /** 실패한 단계. 실패하지 않았으면 null */
  failureStage: RecordingFailureStage | null;
  /** 실패 원인 코드(예: `DOCUMENT_GENERATION_FAILED`). 실패하지 않았으면 null. 코드 목록은 명세에서 아직 초안이에요 */
  failureReason: string | null;

  constructor(raw: GetRecordingResponseRaw) {
    this.recordingId = raw.recordingId;
    this.status = raw.status;
    this.sessionStatus = raw.sessionStatus;
    this.audioUploadStatus = raw.audioUploadStatus;
    this.startedAt = raw.startedAt;
    this.endedAt = raw.endedAt;
    this.durationMillis = raw.durationMillis;
    this.transcriptionStatus = raw.transcriptionStatus;
    this.documentGenerationStatus = raw.documentGenerationStatus;
    this.documentGenerationJobId = raw.documentGenerationJobId;
    this.failureStage = raw.failureStage;
    this.failureReason = raw.failureReason;
  }
}

// POST /api/v1/workspaces/{workspaceId}/recordings/{recordingId}/pause

/** 녹음 일시정지 시 앱이 넘기는 값. 시작 때 보낸 증명 그대로 */
export interface PostRecordingPauseRequestInput {
  tabId: string;
  controlToken: string;
}

/** 녹음 일시정지 요청 본문. 최초 녹음 탭임을 증명해요 */
export class PostRecordingPauseRequestDto {
  /** 녹음을 시작한 최초 탭의 UUID */
  tabId: string;
  /** 녹음 시작 때 보낸 제어 증명(43자 Base64URL) */
  controlToken: string;

  constructor({ tabId, controlToken }: PostRecordingPauseRequestInput) {
    this.tabId = tabId;
    this.controlToken = controlToken;
  }
}

/** 녹음 일시정지의 서버 응답 모양 */
export interface PostRecordingPauseResponseRaw {
  recordingId: number;
  status: RecordingSessionStatus;
  pausedAt: string;
  elapsedMillis: number;
}

/** 녹음 일시정지 응답. 이미 일시정지였어도 같은 모양으로 현재 상태를 돌려줘요 */
export class PostRecordingPauseResponseDto {
  /** 녹음 세션 ID */
  recordingId: number;
  /** 항상 `PAUSED` */
  status: RecordingSessionStatus;
  /** 일시정지 시각(ISO 8601) */
  pausedAt: string;
  /** 일시정지 시점까지 누적 녹음 시간(ms). 일시정지 구간은 빠져요 */
  elapsedMillis: number;

  constructor(raw: PostRecordingPauseResponseRaw) {
    this.recordingId = raw.recordingId;
    this.status = raw.status;
    this.pausedAt = raw.pausedAt;
    this.elapsedMillis = raw.elapsedMillis;
  }
}

// POST /api/v1/workspaces/{workspaceId}/recordings/{recordingId}/resume

/** 녹음 재개 시 앱이 넘기는 값. 시작 때 보낸 증명 그대로 */
export interface PostRecordingResumeRequestInput {
  tabId: string;
  controlToken: string;
}

/** 녹음 재개 요청 본문. 최초 녹음 탭임을 증명해요 */
export class PostRecordingResumeRequestDto {
  /** 녹음을 시작한 최초 탭의 UUID */
  tabId: string;
  /** 녹음 시작 때 보낸 제어 증명(43자 Base64URL) */
  controlToken: string;

  constructor({ tabId, controlToken }: PostRecordingResumeRequestInput) {
    this.tabId = tabId;
    this.controlToken = controlToken;
  }
}

/** 녹음 재개의 서버 응답 모양 */
export interface PostRecordingResumeResponseRaw {
  recordingId: number;
  status: RecordingSessionStatus;
  resumedAt: string;
  elapsedMillis: number;
}

/** 녹음 재개 응답. 이미 녹음 중이었어도 같은 모양으로 현재 상태를 돌려줘요 */
export class PostRecordingResumeResponseDto {
  /** 녹음 세션 ID */
  recordingId: number;
  /** 항상 `RECORDING` */
  status: RecordingSessionStatus;
  /** 재개 시각(ISO 8601) */
  resumedAt: string;
  /** 재개 직전까지 누적 녹음 시간(ms) */
  elapsedMillis: number;

  constructor(raw: PostRecordingResumeResponseRaw) {
    this.recordingId = raw.recordingId;
    this.status = raw.status;
    this.resumedAt = raw.resumedAt;
    this.elapsedMillis = raw.elapsedMillis;
  }
}

// POST /api/v1/workspaces/{workspaceId}/recordings/{recordingId}/end

/** 녹음 종료의 서버 응답 모양 */
export interface PostRecordingEndResponseRaw {
  recordingId: number;
  status: RecordingSessionStatus;
  endedAt: string;
}

/** 녹음 종료 응답. 요청 본문은 없고, 이미 종료된 녹음이면 처음 확정한 결과를 그대로 돌려줘요 */
export class PostRecordingEndResponseDto {
  /** 녹음 세션 ID */
  recordingId: number;
  /** 항상 `ENDED`. 업로드·전사 처리 상태와는 별개예요 */
  status: RecordingSessionStatus;
  /** 서버가 확정한 종료 시각(ISO 8601) */
  endedAt: string;

  constructor(raw: PostRecordingEndResponseRaw) {
    this.recordingId = raw.recordingId;
    this.status = raw.status;
    this.endedAt = raw.endedAt;
  }
}

// POST /api/v1/workspaces/{workspaceId}/recordings/{recordingId}/audio-upload-url

/** 최종 오디오 업로드 URL을 받을 때 앱이 넘기는 값 */
export interface PostRecordingAudioUploadUrlRequestInput {
  contentType: string;
  contentLength: number;
}

/** 최종 오디오 업로드 URL 발급 요청 본문 */
export class PostRecordingAudioUploadUrlRequestDto {
  /** 최종 오디오 파일의 Content-Type. 예: `audio/webm`. PUT 요청에도 같은 값을 보내야 해요 */
  contentType: string;
  /** 최종 오디오 파일 크기(byte, 1 이상). PUT 요청의 Content-Length와 같아야 해요 */
  contentLength: number;

  constructor({
    contentType,
    contentLength,
  }: PostRecordingAudioUploadUrlRequestInput) {
    this.contentType = contentType;
    this.contentLength = contentLength;
  }
}

/** 최종 오디오 업로드 URL 발급의 서버 응답 모양 */
export interface PostRecordingAudioUploadUrlResponseRaw {
  uploadId: number;
  uploadUrl: string;
  expiresAt: string;
}

/** 최종 오디오 업로드 URL 발급 응답. 새 예약은 201, 아직 쓰지 않은 같은 예약의 재발급은 200으로 같은 모양 */
export class PostRecordingAudioUploadUrlResponseDto {
  /** 업로드 예약 ID. 업로드 완료 확인 API에 넘겨요 */
  uploadId: number;
  /** 객체 저장소에 직접 PUT할 Presigned URL(절대 URL) */
  uploadUrl: string;
  /** URL 만료 시각(ISO 8601). 지나면 같은 요청으로 다시 받아요 */
  expiresAt: string;

  constructor(raw: PostRecordingAudioUploadUrlResponseRaw) {
    this.uploadId = raw.uploadId;
    this.uploadUrl = raw.uploadUrl;
    this.expiresAt = raw.expiresAt;
  }
}

// POST /api/v1/workspaces/{workspaceId}/recordings/{recordingId}/audio-upload-complete

/** 최종 오디오 업로드 처리 상태. 완료 확인 응답은 항상 `COMPLETED`예요 */
export type RecordingAudioUploadStatus = "RESERVED" | "COMPLETED";

/** 최종 오디오 업로드 완료 확인 시 앱이 넘기는 값 */
export interface PostRecordingAudioUploadCompleteRequestInput {
  uploadId: number;
}

/** 최종 오디오 업로드 완료 확인 요청 본문 */
export class PostRecordingAudioUploadCompleteRequestDto {
  /** 업로드 URL 발급 응답의 업로드 예약 ID */
  uploadId: number;

  constructor({ uploadId }: PostRecordingAudioUploadCompleteRequestInput) {
    this.uploadId = uploadId;
  }
}

/** 최종 오디오 업로드 완료 확인의 서버 응답 모양 */
export interface PostRecordingAudioUploadCompleteResponseRaw {
  recordingId: number;
  uploadId: number;
  uploadStatus: RecordingAudioUploadStatus;
  completedAt: string;
}

/** 최종 오디오 업로드 완료 확인 응답. 같은 uploadId로 다시 불러도 처음 확정한 결과를 그대로 돌려줘요 */
export class PostRecordingAudioUploadCompleteResponseDto {
  /** 녹음 세션 ID */
  recordingId: number;
  /** 업로드 예약 ID */
  uploadId: number;
  /** 항상 `COMPLETED`. 전사 처리 상태와는 별개예요 */
  uploadStatus: RecordingAudioUploadStatus;
  /** 서버가 업로드 완료를 확정한 시각(ISO 8601). 다시 불러도 바뀌지 않아요 */
  completedAt: string;

  constructor(raw: PostRecordingAudioUploadCompleteResponseRaw) {
    this.recordingId = raw.recordingId;
    this.uploadId = raw.uploadId;
    this.uploadStatus = raw.uploadStatus;
    this.completedAt = raw.completedAt;
  }
}
