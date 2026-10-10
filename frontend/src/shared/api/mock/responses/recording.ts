import type {
  CurrentRecordingResponse,
  RecordingAudioUploadCompleteResponse,
  RecordingAudioUploadUrlResponse,
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

export const currentRecordingResponse = {
  recordingId: 10,
  status: "RECORDING",
  startedAt: fromNow(-12 * MINUTE),
  elapsedMillis: 12 * MINUTE,
} satisfies CurrentRecordingResponse;

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
