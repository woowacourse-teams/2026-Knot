import { httpClient } from "@api/httpClient";

/**
 * @public
 * @category Constants
 * @description CSRF 토큰 조회 API 경로. swagger의 `csrfToken` 쿼리 파라미터는 서버가 주입하는 값이라 클라이언트가 보내지 않아요
 */
export const AUTH_CSRF_API_PATH = "/api/v1/auth/csrf";

/**
 * @public
 * @category Types
 * @interface GetCsrfTokenApiResponse
 * @description CSRF 토큰 조회 응답 타입
 * @property {string} token - 변경 요청의 `X-XSRF-TOKEN` 헤더에 넣을 토큰
 */
export interface GetCsrfTokenApiResponse {
  token: string;
}

/**
 * @public
 * @category Auth
 * @description 변경 요청(POST·PUT)에 붙일 CSRF 토큰을 조회합니다
 * @returns CSRF 토큰
 * @example
 * const { token } = await getCsrfTokenApi();
 */
export const getCsrfTokenApi = async () => {
  const response = await httpClient<GetCsrfTokenApiResponse>({
    method: "get",
    url: AUTH_CSRF_API_PATH,
  });

  return response.data;
};
