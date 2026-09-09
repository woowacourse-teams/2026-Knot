/**
 * 시스템 브라우저 로그인의 인가 URL 조립과 콜백 쿼리 해석 (기획서 5.2 API 계약).
 *
 * 인가 요청: `GET {API}/oauth2/authorization/github?client=desktop&code_challenge=…
 * &code_challenge_method=S256&state=…&return=loopback:{port}` → 백엔드가 302로 GitHub에 보낸다.
 * 콜백: `http://127.0.0.1:{port}/callback?code=&state=`(1차) 또는 `knot://auth/callback?code=&state=`(2차),
 * 실패는 `?error=`. 두 경로 모두 여기의 `parseCallbackParams`로 읽는다.
 */

export const DESKTOP_CLIENT_ID = "desktop";
export const CODE_CHALLENGE_METHOD = "S256";

export interface AuthorizeUrlInput {
  apiOrigin: string;
  challenge: string;
  state: string;
  /** loopback 서버가 듣는 포트. `return=loopback:{port}`로 보낸다 */
  loopbackPort: number;
}

export function buildAuthorizeUrl(input: AuthorizeUrlInput): string {
  const url = new URL("/oauth2/authorization/github", input.apiOrigin);
  url.searchParams.set("client", DESKTOP_CLIENT_ID);
  url.searchParams.set("code_challenge", input.challenge);
  url.searchParams.set("code_challenge_method", CODE_CHALLENGE_METHOD);
  url.searchParams.set("state", input.state);
  url.searchParams.set("return", `loopback:${input.loopbackPort}`);
  return url.toString();
}

/** 콜백 쿼리. `code`·`state`가 함께 오거나 `error`만 온다 */
export interface AuthCallbackParams {
  code?: string;
  state?: string;
  error?: string;
}

/** 빈 문자열은 없는 값으로 본다 */
export function parseCallbackParams(search: URLSearchParams): AuthCallbackParams {
  const params: AuthCallbackParams = {};
  const code = search.get("code");
  const state = search.get("state");
  const error = search.get("error");
  if (code !== null && code.length > 0) params.code = code;
  if (state !== null && state.length > 0) params.state = state;
  if (error !== null && error.length > 0) params.error = error;
  return params;
}

/** 콜백으로 볼 수 있는 쿼리인가 — 코드가 있거나 실패 사유가 있어야 한다 */
export function isCallbackLike(params: AuthCallbackParams): boolean {
  return params.code !== undefined || params.error !== undefined;
}
