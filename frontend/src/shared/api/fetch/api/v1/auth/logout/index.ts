import { httpClient } from "@api/httpClient";

export const AUTH_LOGOUT_API_PATH = "/api/v1/auth/logout";

/**
 * @description 로그인 세션을 끝냈다고 서버에 알립니다. 자격증명이 우리 저장소에만 있어서 실제 로그아웃은 토큰을 지우는 쪽(`useLogout`)이 하고, 이 요청은 204만 받는 훅이에요. 나중에 서버가 기기 세션을 폐기하게 되면 그 자리가 여기입니다
 * @example
 * await logoutApi();
 */
export const logoutApi = async () => {
  await httpClient({
    method: "post",
    url: AUTH_LOGOUT_API_PATH,
  });
};
