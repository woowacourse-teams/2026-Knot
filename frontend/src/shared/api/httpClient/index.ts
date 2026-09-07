import axios from "axios";

import { clearAccessToken, getAccessToken } from "@api/authToken";

const TIMEOUT_DURATION = 10_000;

const AUTHORIZATION_HEADER_NAME = "Authorization";

const UNAUTHORIZED_STATUS = 401;

export const httpClient = axios.create({
  baseURL: process.env.API_BASE_URL,
  timeout: TIMEOUT_DURATION,
});

/**
 * 로그인 상태를 `Authorization: Bearer <JWT>` 헤더로 증명합니다.
 *
 * 토큰이 어디 있는지는 `@api/authToken`이 알아요. 웹은 `localStorage`, 데스크톱 앱은
 * 셸이 OS 키체인 키로 암호화해 둔 파일이라 읽는 데 시간이 걸릴 수 있어 비동기입니다.
 *
 * 요청이 헤더를 직접 정했으면 그대로 둡니다. 가입 완료 요청은 액세스 토큰이 아니라
 * 온보딩 토큰으로 본인을 증명하기 때문이에요.
 */
httpClient.interceptors.request.use(async (config) => {
  if (config.headers.has(AUTHORIZATION_HEADER_NAME)) return config;

  const accessToken = await getAccessToken();
  if (accessToken === null) return config;

  config.headers.set(AUTHORIZATION_HEADER_NAME, `Bearer ${accessToken}`);

  return config;
});

/**
 * 401을 받으면 들고 있던 토큰을 버립니다.
 *
 * 리프레시 토큰이 없어 만료된 토큰으로는 아무것도 할 수 없고, 남겨 두면 이후 요청이
 * 계속 401을 받으면서 로그인한 것처럼 보여요. 화면을 옮기는 일은 `AuthGuard`가 맡습니다.
 */
httpClient.interceptors.response.use(undefined, async (error: unknown) => {
  if (
    axios.isAxiosError(error) &&
    error.response?.status === UNAUTHORIZED_STATUS
  ) {
    await clearAccessToken();
  }

  throw error;
});
