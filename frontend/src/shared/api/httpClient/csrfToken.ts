import { getCsrfTokenApi } from "@api/fetch/api/v1/auth/csrf";

/**
 * CSRF 토큰 저장소.
 *
 * 서버는 `XSRF-TOKEN` 쿠키와 이 헤더 값을 비교해 우리 화면에서 온 요청인지 판별해요.
 * API가 다른 오리진이라 그 쿠키를 읽을 수 없어서 값을 발급 API로 받아옵니다.
 *
 * 쓰는 곳은 두 군데예요. axios 요청은 httpClient 인터셉터가 자동으로,
 * SSE 요청은 네이티브 fetch 호출부가 직접 가져다 씁니다.
 */

/** 이 메서드들만 CSRF 토큰을 요구해요. */
const MUTATING_METHODS = ["post", "put", "patch", "delete"];

export const CSRF_HEADER_NAME = "X-XSRF-TOKEN";

let csrfToken: string | undefined;

/** 발급 중인 요청. 변경 요청이 동시에 나가도 발급은 한 번만 하려고 들고 있어요. */
let csrfTokenRequest: Promise<string> | undefined;

export const isMutatingRequest = (method?: string) =>
  MUTATING_METHODS.includes((method ?? "get").toLowerCase());

const loadCsrfToken = () => {
  csrfTokenRequest =
    csrfTokenRequest ??
    getCsrfTokenApi()
      .then(({ token }) => {
        csrfToken = token;

        return token;
      })
      .finally(() => {
        csrfTokenRequest = undefined;
      });

  return csrfTokenRequest;
};

export const getCsrfToken = async () => csrfToken ?? (await loadCsrfToken());

export const reloadCsrfToken = () => {
  csrfToken = undefined;

  return loadCsrfToken();
};
