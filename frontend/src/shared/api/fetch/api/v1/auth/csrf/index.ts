import {
  GetCsrfTokenResponseDto,
  type GetCsrfTokenResponseRaw,
} from "@api/dto/auth";
import { httpClient } from "@api/httpClient";

export const AUTH_CSRF_API_PATH = "/api/v1/auth/csrf";

/**
 * @description 변경 요청(POST·PUT)에 붙일 CSRF 토큰을 조회합니다
 * @returns CSRF 토큰
 * @example
 * const { token } = await getCsrfTokenApi();
 */
export const getCsrfTokenApi = async () => {
  const response = await httpClient<GetCsrfTokenResponseRaw>({
    method: "get",
    url: AUTH_CSRF_API_PATH,
  });

  return new GetCsrfTokenResponseDto(response.data);
};
