/**
 * 녹음 DTO
 *
 * - POST /api/v1/workspaces/{workspaceId}/recordings
 * - POST /api/v1/workspaces/{workspaceId}/recordings/{recordingId}/pause
 * - POST /api/v1/workspaces/{workspaceId}/recordings/{recordingId}/resume
 * - POST /api/v1/workspaces/{workspaceId}/recordings/{recordingId}/end
 * - POST /api/v1/workspaces/{workspaceId}/recordings/{recordingId}/audio-upload-url
 */

/** 녹음 세션 상태. 시작·일시정지·재개·종료 응답이 공유하는 서버 값 */
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
