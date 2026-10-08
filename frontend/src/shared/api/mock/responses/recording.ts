import type {
  RecordingAudioUploadCompleteResponse,
  RecordingAudioUploadUrlResponse,
  RecordingDetailResponse,
  RecordingEndResponse,
  RecordingPauseResponse,
  RecordingResumeResponse,
  RecordingStartResponse,
} from "@api/mock/types/recording";

const MINUTE = 60 * 1000;

// 고정 시각은 언젠가 만료·과거가 되므로 지금 기준으로 만들어요
const fromNow = (offset: number) => new Date(Date.now() + offset).toISOString();

/**
 * Presigned URL이 가리키는 가짜 객체 저장소. 실제 저장소는 우리 API와 오리진이 달라
 * 와일드카드 대신 이 오리진으로 PUT을 가로채요
 */
export const MOCK_AUDIO_STORAGE_ORIGIN = "https://audio-storage.knot.mock";

export const recordingStartResponse = {
  recordingId: 10,
  status: "RECORDING",
  startedAt: fromNow(0),
} satisfies RecordingStartResponse;

export const recordingPauseResponse = {
  recordingId: 10,
  status: "PAUSED",
  pausedAt: fromNow(0),
  elapsedMillis: 10 * MINUTE,
} satisfies RecordingPauseResponse;

export const recordingResumeResponse = {
  recordingId: 10,
  status: "RECORDING",
  resumedAt: fromNow(0),
  elapsedMillis: 10 * MINUTE,
} satisfies RecordingResumeResponse;

export const recordingEndResponse = {
  recordingId: 10,
  status: "ENDED",
  endedAt: fromNow(0),
} satisfies RecordingEndResponse;

// 32분 녹음을 8분 전에 끝낸 뒤의 공통 값이에요
const endedRecording = {
  sessionStatus: "ENDED",
  startedAt: fromNow(-40 * MINUTE),
  endedAt: fromNow(-8 * MINUTE),
  durationMillis: 32 * MINUTE,
} satisfies Partial<RecordingDetailResponse>;

// 녹음을 끝낸 뒤의 상태를 녹음마다 하나씩 둬, 정리 화면 주소의 녹음 ID만 바꿔 각 상태를 볼 수 있어요.
// 10은 녹음 시작·종료 mock과 같은 녹음이라, 끝낸 직후에 보게 되는 정리 중으로 뒀어요
export const recordingDetailsResponse = [
  {
    ...endedRecording,
    recordingId: 10,
    status: "PROCESSING",
    audioUploadStatus: "COMPLETED",
    transcriptionStatus: "SUCCEEDED",
    documentGenerationStatus: "RUNNING",
    documentGenerationJobId: 86,
    failureStage: null,
    failureReason: null,
  },
  {
    ...endedRecording,
    recordingId: 11,
    status: "NO_CONTENT",
    audioUploadStatus: "COMPLETED",
    transcriptionStatus: "SUCCEEDED",
    documentGenerationStatus: "NOT_STARTED",
    documentGenerationJobId: null,
    failureStage: null,
    failureReason: null,
  },
  {
    ...endedRecording,
    recordingId: 12,
    status: "FAILED",
    audioUploadStatus: "COMPLETED",
    transcriptionStatus: "SUCCEEDED",
    documentGenerationStatus: "FAILED",
    documentGenerationJobId: 88,
    failureStage: "DOCUMENT_GENERATION",
    failureReason: "DOCUMENT_GENERATION_FAILED",
  },
  {
    ...endedRecording,
    recordingId: 13,
    status: "FAILED",
    audioUploadStatus: "COMPLETED",
    transcriptionStatus: "FAILED",
    documentGenerationStatus: "NOT_STARTED",
    documentGenerationJobId: null,
    failureStage: "TRANSCRIPTION",
    failureReason: "TRANSCRIPTION_FAILED",
  },
  {
    ...endedRecording,
    recordingId: 14,
    status: "COMPLETED",
    audioUploadStatus: "COMPLETED",
    transcriptionStatus: "SUCCEEDED",
    documentGenerationStatus: "SUCCEEDED",
    documentGenerationJobId: 87,
    failureStage: null,
    failureReason: null,
  },
] satisfies RecordingDetailResponse[];

export const recordingAudioUploadUrlResponse = {
  uploadId: 300,
  uploadUrl: `${MOCK_AUDIO_STORAGE_ORIGIN}/recordings/10/audio`,
  expiresAt: fromNow(5 * MINUTE),
} satisfies RecordingAudioUploadUrlResponse;

export const recordingAudioUploadCompleteResponse = {
  recordingId: 10,
  uploadId: 300,
  uploadStatus: "COMPLETED",
  completedAt: fromNow(0),
} satisfies RecordingAudioUploadCompleteResponse;
