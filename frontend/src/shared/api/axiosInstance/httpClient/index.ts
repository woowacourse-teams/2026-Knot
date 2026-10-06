import axios from "axios";

import { attachCsrfToken, retryWithNewCsrfToken } from "../interceptors/csrf";

const TIMEOUT_DURATION = 10_000;

/** 우리 API로 보내는 기본 클라이언트. 실패하면 AxiosError를 그대로 던져요. */
export const httpClient = axios.create({
  baseURL: process.env.API_BASE_URL,
  timeout: TIMEOUT_DURATION,

  /**
   * 인증 쿠키를 함께 보냅니다.
   *
   * 로그인 상태는 `__Host-KNOT_ACCESS_TOKEN` 쿠키로만 유지되고 자바스크립트가 읽을 수
   * 없어요. 이 값이 없으면 쿠키가 실리지 않아 인증이 필요한 요청이 전부 401이 됩니다.
   */
  withCredentials: true,
});

httpClient.interceptors.request.use(attachCsrfToken);
httpClient.interceptors.response.use(
  undefined,
  retryWithNewCsrfToken(httpClient),
);
