import axios from "axios";

const API_ERROR_NAME = "ApiError";

/** 서버가 오류 응답 본문에 담아 주는 값 */
interface ApiErrorResponse {
  code?: string;
}

/** `toApiError`가 던지는 오류. axios를 몰라도 상태와 서버 오류 코드로 분기할 수 있어요. */
export interface ApiError extends Error {
  /** HTTP 상태. 응답을 받지 못한 네트워크 오류면 없어요 */
  status?: number;
  /** 서버 오류 코드(예: `RECORDING_ALREADY_ENDED`). 본문에 없으면 없어요 */
  code?: string;
}

/** AxiosError를 `ApiError`로 바꿔 던지는 응답 인터셉터. axios가 만든 오류가 아니면 그대로 던져요. */
export const toApiError = (error: unknown) => {
  if (!axios.isAxiosError<ApiErrorResponse>(error)) throw error;

  const apiError: ApiError = Object.assign(new Error(error.message), {
    name: API_ERROR_NAME,
    status: error.response?.status,
    code: error.response?.data?.code,
    // 원래 AxiosError는 콘솔에서 요청 정보를 볼 수 있게 남겨요
    cause: error,
  });

  throw apiError;
};

/**
 * `toApiError`가 던진 오류인지 판별해요.
 *
 * 클래스는 `dto/` 밖에서 쓰지 않는 규칙이라 `instanceof` 대신 `name`으로 구분해요.
 */
export const isApiError = (error: unknown): error is ApiError =>
  error instanceof Error && error.name === API_ERROR_NAME;
