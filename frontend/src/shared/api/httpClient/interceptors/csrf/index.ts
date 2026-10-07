import type { AxiosInstance } from "axios";
import axios from "axios";

import type { HttpInterceptor } from "../../types/interceptor";

/** CSRF 토큰을 발급받는 경로. 응답 본문으로 토큰을 주고 `XSRF-TOKEN` 쿠키도 함께 내려줘요. */
const CSRF_API_PATH = "/api/v1/auth/csrf";

const CSRF_HEADER_NAME = "X-XSRF-TOKEN";

/** 서버 상태를 바꾸는 메서드. 이 요청들만 CSRF 토큰을 요구해요. */
const MUTATING_METHODS = ["post", "put", "patch", "delete"];

interface CsrfTokenResponse {
  token: string;
}

/**
 * 서버가 준 CSRF 토큰. 요청마다 새로 받지 않도록 들고 있어요.
 *
 * 로그인처럼 인증 상태가 바뀌면 서버가 토큰을 새로 만들어서 이 값이 낡을 수 있습니다.
 * 그때는 403이 오는데, `csrfInterceptor`가 다시 받아 한 번 더 보냅니다.
 */
let csrfToken: string | undefined;

const loadCsrfToken = async (client: AxiosInstance) => {
  const { data } = await client.get<CsrfTokenResponse>(CSRF_API_PATH);
  csrfToken = data.token;

  return csrfToken;
};

/**
 * 지금 들고 있는 CSRF 토큰을 돌려주고, 없으면 `client`로 받아옵니다.
 *
 * SSE 응답은 axios(XHR 어댑터)로 읽을 수 없어 fetch로 직접 보내야 하는데,
 * 그 요청도 상태를 바꾸는 POST라 같은 토큰이 필요합니다. 요청마다 새로 받지 않도록
 * 인터셉터와 이 캐시를 함께 씁니다.
 */
export const getCsrfToken = async (client: AxiosInstance) =>
  csrfToken ?? (await loadCsrfToken(client));

const isMutatingRequest = (method?: string) =>
  MUTATING_METHODS.includes((method ?? "get").toLowerCase());

const isForbidden = (error: unknown) =>
  axios.isAxiosError(error) && error.response?.status === 403;

/**
 * 상태를 바꾸는 요청에 CSRF 토큰을 헤더로 붙이고, 토큰이 낡아 403이 오면 새로 받아 한 번만 다시 보냅니다.
 *
 * 서버는 이 헤더와 `XSRF-TOKEN` 쿠키를 비교해 우리 화면에서 온 요청인지 판별해요.
 * 쿠키는 브라우저가 알아서 보내지만, 프론트와 API의 도메인이 달라
 * 자바스크립트가 그 쿠키를 읽을 수 없습니다. 그래서 값을 `/api/v1/auth/csrf`로 받아 씁니다.
 * 발급 요청은 조회라 이 인터셉터를 그냥 지나가요.
 *
 * 다시 보낸 요청의 실패는 그대로 던져, 계속 실패하는 상황에서 요청이 끝없이 반복되지 않게 해요.
 */
export const csrfInterceptor: HttpInterceptor =
  (next, client) => async (config) => {
    if (!isMutatingRequest(config.method)) return next(config);

    config.headers.set(CSRF_HEADER_NAME, await getCsrfToken(client));

    try {
      return await next(config);
    } catch (error) {
      if (!isForbidden(error)) throw error;

      config.headers.set(CSRF_HEADER_NAME, await loadCsrfToken(client));

      return next(config);
    }
  };
