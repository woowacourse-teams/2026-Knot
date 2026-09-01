import axios from "axios";
import type { InternalAxiosRequestConfig } from "axios";
import {
  CSRF_HEADER_NAME,
  getCsrfToken,
  isMutatingRequest,
  reloadCsrfToken,
} from "@/shared/api/httpClient/csrfToken";
import { AUTH_CSRF_API_PATH } from "@api/fetch/api/v1/auth/csrf";

interface RetriableConfig extends InternalAxiosRequestConfig {
  isCsrfRetried?: boolean;
}

const TIMEOUT_DURATION = 10_000;

export const httpClient = axios.create({
  baseURL: process.env.API_BASE_URL,
  timeout: TIMEOUT_DURATION,
  withCredentials: true,
});

httpClient.interceptors.request.use(async (config) => {
  if (!isMutatingRequest(config.method)) return config;

  config.headers.set(CSRF_HEADER_NAME, await getCsrfToken());

  return config;
});

/**
 * 토큰이 낡아 403이 오면 새로 받아 한 번만 다시 보냅니다.
 *
 * 발급 요청 자신과 이미 재시도한 요청은 제외해요.
 * 401은 로그인 화면 이동 같은 공통 처리가 정해지지 않아 각 호출부가 받습니다.
 */
httpClient.interceptors.response.use(undefined, async (error: unknown) => {
  if (!axios.isAxiosError(error)) throw error;

  const config = error.config as RetriableConfig | undefined;
  const canRetry =
    error.response?.status === 403 &&
    config !== undefined &&
    config.url !== AUTH_CSRF_API_PATH &&
    !config.isCsrfRetried;

  if (!canRetry) throw error;

  config.isCsrfRetried = true;
  await reloadCsrfToken();

  return httpClient.request(config);
});
