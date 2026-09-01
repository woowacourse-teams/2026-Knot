import { httpClient } from "@api/httpClient";

// swagger의 `csrfToken` 쿼리 파라미터는 서버가 주입하는 값이라 클라이언트가 보내지 않아요
export const AUTH_CSRF_API_PATH = "/api/v1/auth/csrf";

interface GetCsrfTokenApiResponse {
  /** 변경 요청의 `X-XSRF-TOKEN` 헤더에 넣을 토큰 */
  token: string;
}

/**
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
