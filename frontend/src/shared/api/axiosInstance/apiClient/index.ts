import axios from "axios";

import { toApiError } from "../interceptors/apiError";
import { attachCsrfToken, retryWithNewCsrfToken } from "../interceptors/csrf";

export { type ApiError, isApiError } from "../interceptors/apiError";

const TIMEOUT_DURATION = 10_000;

/**
 * 오류를 `ApiError`로 바꿔 던지는 클라이언트. 요청 설정과 CSRF 처리는 `httpClient`와 같아요.
 *
 * 기존 요청 함수들은 AxiosError를 전제로 오류를 판별하고 있어, 그쪽에 영향이 가지 않도록
 * 인스턴스를 나눴어요. 오류를 앱 쪽 모양으로 다루고 싶은 요청 함수만 이 인스턴스로 보내요.
 */
export const apiClient = axios.create({
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

apiClient.interceptors.request.use(attachCsrfToken);
// 응답 인터셉터는 등록 순서대로 돌아요. 403 재시도는 AxiosError에만 동작하므로 오류 변환을 마지막에 둬요
apiClient.interceptors.response.use(
  undefined,
  retryWithNewCsrfToken(apiClient),
);
apiClient.interceptors.response.use(undefined, toApiError);
