import { httpClient } from "@api/httpClient";

export const AUTH_LOGOUT_API_PATH = "/api/v1/auth/logout";

/**
 * @description 로그인 세션을 끝냅니다. 서버가 접근 토큰·온보딩 토큰 쿠키를 만료시키므로 보낼 값도 받을 값도 없어요. 쿠키는 `httpOnly`라 자바스크립트가 지울 수 없어서, 로그아웃은 이 요청으로만 이뤄집니다. 상태를 바꾸는 요청이라 CSRF 토큰이 필요하지만 그건 `httpClient`가 붙여요
 * @example
 * await logoutApi();
 */
export const logoutApi = async () => {
  await httpClient({
    method: "post",
    url: AUTH_LOGOUT_API_PATH,
  });
};
