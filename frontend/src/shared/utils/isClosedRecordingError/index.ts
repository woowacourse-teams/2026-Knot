import { isHttpError } from "@api/httpClient/error";

/** 서버에서 이미 끝났거나 버려진 녹음이라 이 탭의 녹음도 버려야 하는 오류 코드 */
const CLOSED_RECORDING_ERROR_CODES = [
  "RECORDING_ALREADY_DISCARDED",
  "RECORDING_ALREADY_ENDED",
];

/**
 * 서버가 이미 끝났거나 버려진 녹음이라고 응답한 오류인지 확인해요.
 * HTTP 상태는 보지 않고 서버 오류 코드로만 판단하며, 응답이 없는 네트워크 오류는 `false`예요.
 */
export const isClosedRecordingError = (error: unknown) =>
  isHttpError(error) && CLOSED_RECORDING_ERROR_CODES.includes(error.code ?? "");
