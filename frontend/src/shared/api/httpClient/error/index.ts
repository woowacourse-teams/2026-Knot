/**
 * httpClient 요청이 실패한 이유. 화면은 상태 코드 대신 이 값으로 분기해요.
 *
 * - `clientError`: 위 4xx 외의 나머지 4xx
 * - `serverError`: 5xx
 * - `network`: 응답을 받지 못한 실패(네트워크 끊김·timeout)
 */
export const HTTP_ERROR_TYPE = {
  badRequest: "badRequest",
  unauthorized: "unauthorized",
  forbidden: "forbidden",
  notFound: "notFound",
  conflict: "conflict",
  tooManyRequests: "tooManyRequests",
  clientError: "clientError",
  serverError: "serverError",
  network: "network",
} as const;

export type HttpErrorType =
  (typeof HTTP_ERROR_TYPE)[keyof typeof HTTP_ERROR_TYPE];

const HTTP_ERROR_NAME = "HttpError";

const CLIENT_ERROR_TYPES: HttpErrorType[] = [
  HTTP_ERROR_TYPE.badRequest,
  HTTP_ERROR_TYPE.unauthorized,
  HTTP_ERROR_TYPE.forbidden,
  HTTP_ERROR_TYPE.notFound,
  HTTP_ERROR_TYPE.conflict,
  HTTP_ERROR_TYPE.tooManyRequests,
  HTTP_ERROR_TYPE.clientError,
];

/** httpClient가 던지는 실패. `httpErrorInterceptor`가 axios 오류를 이 모양으로 바꿔요. */
export interface HttpError extends Error {
  /** 실패한 이유 */
  type: HttpErrorType;
  /** 응답 본문의 서버 오류 코드. 본문이 없으면 `undefined`예요. */
  code?: string;
  /** 요청을 고쳐야 하는 실패(4xx)라 다시 보내도 결과가 같은지 여부 */
  isClientError: boolean;
}

interface CreateHttpErrorParams {
  type: HttpErrorType;
  code?: string;
  /** 원래 오류. 디버깅용으로 남겨요. */
  cause?: unknown;
}

export const createHttpError = ({ type, code, cause }: CreateHttpErrorParams) =>
  Object.assign(new Error(`HTTP 요청 실패: ${type}`), {
    name: HTTP_ERROR_NAME,
    cause,
    type,
    code,
    isClientError: CLIENT_ERROR_TYPES.includes(type),
  }) as HttpError;

/**
 * httpClient 요청의 실패인지 확인해요. `type`을 주면 그 이유로 실패했는지까지 봐요.
 *
 * @example
 * if (isHttpError(error, HTTP_ERROR_TYPE.notFound)) navigateToWorkspace();
 */
export const isHttpError = (
  error: unknown,
  type?: HttpErrorType,
): error is HttpError =>
  error instanceof Error &&
  error.name === HTTP_ERROR_NAME &&
  (type === undefined || (error as HttpError).type === type);
