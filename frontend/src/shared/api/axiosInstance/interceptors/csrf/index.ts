import type { AxiosInstance, InternalAxiosRequestConfig } from "axios";
import axios from "axios";

/** CSRF 토큰을 발급받는 경로. 응답 본문으로 토큰을 주고 `XSRF-TOKEN` 쿠키도 함께 내려줘요. */
const CSRF_API_PATH = "/api/v1/auth/csrf";

const TIMEOUT_DURATION = 10_000;

const CSRF_HEADER_NAME = "X-XSRF-TOKEN";

/** 서버 상태를 바꾸는 메서드. 이 요청들만 CSRF 토큰을 요구해요. */
const MUTATING_METHODS = ["post", "put", "patch", "delete"];

interface CsrfTokenResponse {
  token: string;
}

/** 403을 만나 토큰을 새로 받고 한 번 다시 보낸 요청인지 표시해요. 무한 재시도를 막습니다. */
interface RetriableConfig extends InternalAxiosRequestConfig {
  isCsrfRetried?: boolean;
}

// 토큰 조회는 상태를 바꾸지 않는 GET이라 아래 인터셉터가 필요 없어, 인터셉터 없는 인스턴스로 받아요
const csrfClient = axios.create({
  baseURL: process.env.API_BASE_URL,
  timeout: TIMEOUT_DURATION,
  // 토큰과 함께 내려오는 `XSRF-TOKEN` 쿠키를 받으려면 쿠키를 주고받아야 해요
  withCredentials: true,
});

/**
 * 서버가 준 CSRF 토큰. 요청마다 새로 받지 않도록 들고 있어요.
 *
 * 로그인처럼 인증 상태가 바뀌면 서버가 토큰을 새로 만들어서 이 값이 낡을 수 있습니다.
 * 그때는 403이 오는데, `retryWithNewCsrfToken`이 다시 받아 한 번 더 보냅니다.
 * 여러 클라이언트가 이 값 하나를 함께 씁니다.
 */
let csrfToken: string | undefined;

const loadCsrfToken = async () => {
  const { data } = await csrfClient.get<CsrfTokenResponse>(CSRF_API_PATH);
  csrfToken = data.token;

  return csrfToken;
};

/**
 * 지금 들고 있는 CSRF 토큰을 돌려주고, 없으면 받아옵니다.
 *
 * SSE 응답은 axios(XHR 어댑터)로 읽을 수 없어 fetch로 직접 보내야 하는데,
 * 그 요청도 상태를 바꾸는 POST라 같은 토큰이 필요합니다. 요청마다 새로 받지 않도록
 * 아래 인터셉터와 이 캐시를 함께 씁니다.
 */
export const getCsrfToken = async () => csrfToken ?? (await loadCsrfToken());

const isMutatingRequest = (method?: string) =>
  MUTATING_METHODS.includes((method ?? "get").toLowerCase());

/**
 * 상태를 바꾸는 요청에 CSRF 토큰을 헤더로 붙이는 요청 인터셉터.
 *
 * 서버는 이 헤더와 `XSRF-TOKEN` 쿠키를 비교해 우리 화면에서 온 요청인지 판별해요.
 * 쿠키는 브라우저가 알아서 보내지만, 프론트와 API의 도메인이 달라
 * 자바스크립트가 그 쿠키를 읽을 수 없습니다. 그래서 값을 `/api/v1/auth/csrf`로 받아 씁니다.
 *
 * 조회 요청은 상태를 바꾸지 않아 토큰이 필요 없어요.
 */
export const attachCsrfToken = async (config: InternalAxiosRequestConfig) => {
  if (!isMutatingRequest(config.method)) return config;

  config.headers.set(CSRF_HEADER_NAME, await getCsrfToken());

  return config;
};

/**
 * CSRF 토큰이 낡아 403이 오면 새로 받아 `instance`로 한 번만 다시 보내는 응답 인터셉터를 만들어요.
 *
 * 토큰을 받아오는 요청 자체가 실패한 경우와 이미 한 번 다시 보낸 요청은 건너뜁니다.
 * 그러지 않으면 계속 실패하는 상황에서 요청이 끝없이 반복돼요.
 */
export const retryWithNewCsrfToken =
  (instance: AxiosInstance) => async (error: unknown) => {
    if (!axios.isAxiosError(error)) throw error;

    const config = error.config as RetriableConfig | undefined;
    const canRetry =
      error.response?.status === 403 &&
      config !== undefined &&
      config.url !== CSRF_API_PATH &&
      !config.isCsrfRetried;

    if (!canRetry) throw error;

    config.isCsrfRetried = true;
    await loadCsrfToken();

    return instance.request(config);
  };
