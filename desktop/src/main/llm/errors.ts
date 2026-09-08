/**
 * 사용자 LLM 오류 코드 (기획서 6.4 오류 매핑, 6.2 표와 같은 코드).
 *
 * 코드·문구는 백엔드 `ChatErrorCode`와 같게 둔다. 웹은 SSE와 IPC 어느 경로에서 왔든
 * 같은 `error {code, message}`를 받는다(불변 계약 1번).
 */

export type LlmErrorCode =
  | "LLM_CONFIGURATION_INVALID"
  | "LLM_STREAM_FAILED"
  | "LLM_STREAM_TIMEOUT"
  | "LLM_RATE_LIMITED"
  | "LLM_REFUSED";

export const LLM_ERROR_MESSAGES: Readonly<Record<LlmErrorCode, string>> = {
  LLM_CONFIGURATION_INVALID: "LLM 설정이 올바르지 않습니다",
  LLM_STREAM_FAILED: "답변 생성에 실패했습니다",
  LLM_STREAM_TIMEOUT: "답변 생성 시간이 초과되었습니다",
  LLM_RATE_LIMITED: "답변 생성 요청이 많아 잠시 후 다시 시도해야 합니다",
  LLM_REFUSED: "이 질문에 대한 답변 생성이 거부되었습니다",
};

export class LlmError extends Error {
  readonly code: LlmErrorCode;

  /** @param message 사용자에게 보여줄 문구. 생략하면 코드의 기본 문구 */
  constructor(code: LlmErrorCode, message: string = LLM_ERROR_MESSAGES[code]) {
    super(message);
    this.name = "LlmError";
    this.code = code;
  }
}

/** HTTP 상태 → 코드. 401·403은 키·권한, 429·529(Anthropic overloaded)는 한도 */
export function llmErrorCodeForStatus(status: number): LlmErrorCode {
  switch (status) {
    case 401:
    case 403:
      return "LLM_CONFIGURATION_INVALID";
    case 429:
    case 529:
      return "LLM_RATE_LIMITED";
    default:
      return "LLM_STREAM_FAILED";
  }
}

/** Anthropic 스트림 `error` 이벤트의 `error.type` → 코드 */
export function llmErrorCodeForAnthropicErrorType(errorType: string): LlmErrorCode {
  switch (errorType) {
    case "authentication_error":
    case "permission_error":
      return "LLM_CONFIGURATION_INVALID";
    case "rate_limit_error":
    case "overloaded_error":
      return "LLM_RATE_LIMITED";
    default:
      return "LLM_STREAM_FAILED";
  }
}

/** `AbortController.abort()`로 끝난 fetch·스트림 오류인가 */
export function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === "AbortError";
}
