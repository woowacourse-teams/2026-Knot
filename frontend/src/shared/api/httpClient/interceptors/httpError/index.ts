import axios from "axios";

import type { HttpErrorType } from "../../error";
import { createHttpError, HTTP_ERROR_TYPE } from "../../error";
import type { HttpInterceptor } from "../../types/interceptor";

const STATUS_ERROR_TYPE: Record<number, HttpErrorType> = {
  400: HTTP_ERROR_TYPE.badRequest,
  401: HTTP_ERROR_TYPE.unauthorized,
  403: HTTP_ERROR_TYPE.forbidden,
  404: HTTP_ERROR_TYPE.notFound,
  409: HTTP_ERROR_TYPE.conflict,
  429: HTTP_ERROR_TYPE.tooManyRequests,
};

const toErrorType = (status?: number) => {
  if (status === undefined) return HTTP_ERROR_TYPE.network;
  if (status in STATUS_ERROR_TYPE) return STATUS_ERROR_TYPE[status];

  return status < 500 ? HTTP_ERROR_TYPE.clientError : HTTP_ERROR_TYPE.serverError;
};

const toErrorCode = (data: unknown) =>
  typeof data === "object" &&
  data !== null &&
  "code" in data &&
  typeof data.code === "string"
    ? data.code
    : undefined;

/**
 * axios 오류를 `HttpError`로 바꿔 던집니다.
 *
 * 화면이 axios와 상태 코드를 몰라도 `isHttpError(error, HTTP_ERROR_TYPE.notFound)`처럼
 * 실패 이유로 분기하게 하려는 거예요.
 * 취소된 요청은 react-query가 취소로 알아봐야 해서 그대로 던지고, axios 오류가 아닌 것도 건드리지 않아요.
 *
 * 다른 인터셉터는 axios 오류를 보고 판단하므로(CSRF의 403 재시도 등) 가장 먼저 `use`해 바깥을 감싸야 해요.
 */
export const httpErrorInterceptor: HttpInterceptor =
  (next) => async (config) => {
    try {
      return await next(config);
    } catch (error) {
      if (!axios.isAxiosError(error) || axios.isCancel(error)) throw error;

      throw createHttpError({
        type: toErrorType(error.response?.status),
        code: toErrorCode(error.response?.data),
        cause: error,
      });
    }
  };
