import axios from "axios";

const TIMEOUT_DURATION = 10_000;

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

  /**
   * CSRF 토큰을 헤더로 붙입니다.
   *
   * 서버가 내려준 `XSRF-TOKEN` 쿠키를 읽어 `X-XSRF-TOKEN` 헤더로 보내요.
   * axios는 같은 출처일 때만 이 일을 자동으로 하는데, 프론트와 API의 도메인이 달라서
   * 켜주지 않으면 헤더가 붙지 않고 서버가 403으로 막습니다.
   */
  withXSRFToken: true,
});
