import { HTTP_ERROR_TYPE, isHttpError } from "@api/httpClient/error";

/**
 * 인증이 없거나 만료된 401 응답인지 판별해요.
 *
 * 온보딩 토큰이나 접근 토큰이 유효하지 않은 상태라, 문구로 알리지 않고 로그인 화면으로 돌려보내야 해요.
 */
export const isUnauthorizedError = (error: unknown) =>
  isHttpError(error, HTTP_ERROR_TYPE.unauthorized);
