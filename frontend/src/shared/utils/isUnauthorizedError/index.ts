import axios from "axios";

/**
 * 인증이 없거나 만료된 응답(401)인지 판별합니다.
 *
 * 로그인 상태는 `httpOnly` 쿠키로만 유지되어 자바스크립트가 확인할 수 없으므로,
 * 로그인이 풀렸다는 사실도 이 상태 코드로만 알 수 있어요.
 * 그래서 401은 문구로 안내하지 않고 로그인 화면으로 돌려보내는 신호로 씁니다.
 *
 * 네트워크 오류나 5xx는 로그인이 풀린 게 아니므로 여기서 걸러지지 않아요.
 * 그때는 로그인 화면으로 보내지 말고 다시 시도할 수 있게 안내해야 합니다.
 */
export const isUnauthorizedError = (error: unknown) =>
  axios.isAxiosError(error) && error.response?.status === 401;
