/**
 * 사용자 Claude 구독 OAuth — 인가 URL 조립·토큰 교환·갱신 (기획서 6.5, 로드맵 Q61 a, 지식 §6.9).
 *
 * 앱이 Claude Code의 OAuth 흐름을 재현한다: 시스템 브라우저로 `https://claude.ai/oauth/authorize`를
 * 열고(PKCE `S256`, loopback 콜백), 받은 authorization code를 `https://platform.claude.com/v1/oauth/token`
 * 에서 access(`sk-ant-oat…`)·refresh(`sk-ant-ort…`) 토큰으로 교환한다. 갱신도 같은 엔드포인트다.
 *
 * - 토큰 엔드포인트 요청 본문은 JSON이며 `Authorization` 헤더를 붙이지 않는다(자격증명은 본문의 code·refresh 토큰).
 * - 응답 헤더는 30초 안에 와야 한다. 네트워크 오류·타임아웃은 `CLAUDE_OAUTH_UNREACHABLE`, 모양이 계약과 다르면
 *   `CLAUDE_OAUTH_MALFORMED`, HTTP 오류는 본문 `{error, error_description}`(RFC 6749 §5.2)을 그대로 올린다.
 * - 코드·verifier·state·토큰 값은 로그에 남기지 않는다.
 *
 * 이 형태는 Anthropic `legal-and-compliance`가 금지하는 것에 해당하며 재량 허용 구간에서 운영하는,
 * 사용자가 감수하기로 한 정책 리스크다(로드맵 R28·R29).
 */

import { logger } from "../logging";

/** Claude Code의 공개 OAuth client_id(지식 §6.9 실측). 앱이 별도 client를 등록하지 않는다 */
export const SUBSCRIPTION_CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e";
export const SUBSCRIPTION_AUTHORIZE_URL = "https://claude.ai/oauth/authorize";
export const SUBSCRIPTION_TOKEN_URL = "https://platform.claude.com/v1/oauth/token";
/** Knot에 필요한 최소 스코프(로드맵 Q61 a). Aside가 요구하는 세션·MCP·파일 스코프는 받지 않는다 */
export const SUBSCRIPTION_SCOPES = ["org:create_api_key", "user:profile", "user:inference"] as const;
export const SUBSCRIPTION_CALLBACK_PATH = "/callback";
export const SUBSCRIPTION_AUTH_TIMEOUT_MS = 30_000;

export const SUBSCRIPTION_AUTH_ERRORS = {
  unreachable: { code: "CLAUDE_OAUTH_UNREACHABLE", message: "Claude 인증 서버에 연결하지 못했어요" },
  malformed: { code: "CLAUDE_OAUTH_MALFORMED", message: "Claude 인증 서버 응답이 계약과 달라요" },
  unknown: { code: "CLAUDE_OAUTH_FAILED", message: "Claude 구독 로그인에 실패했어요" },
} as const;

/** RFC 6749 §5.2 — 리프레시 토큰이 폐기·만료됐을 때 토큰 엔드포인트가 주는 오류 코드 */
export const INVALID_GRANT = "invalid_grant";

/**
 * loopback 콜백 주소. Claude Code가 등록한 redirect 패턴(`http://localhost:<port>/callback`)을 따르며
 * 서버는 `127.0.0.1`에 듣는다(로드맵 Q63 — 브라우저는 `localhost`를 IPv4·IPv6 양쪽으로 시도한다).
 */
export function buildSubscriptionRedirectUri(port: number): string {
  return `http://localhost:${port}${SUBSCRIPTION_CALLBACK_PATH}`;
}

export interface SubscriptionAuthorizeInput {
  challenge: string;
  state: string;
  redirectUri: string;
}

export function buildSubscriptionAuthorizeUrl(input: SubscriptionAuthorizeInput): string {
  const url = new URL(SUBSCRIPTION_AUTHORIZE_URL);
  // Claude Code 흐름의 고정 파라미터. `code=true`는 인가 코드를 콜백으로 돌려받겠다는 표시다
  url.searchParams.set("code", "true");
  url.searchParams.set("client_id", SUBSCRIPTION_CLIENT_ID);
  url.searchParams.set("response_type", "code");
  url.searchParams.set("redirect_uri", input.redirectUri);
  url.searchParams.set("scope", SUBSCRIPTION_SCOPES.join(" "));
  url.searchParams.set("code_challenge", input.challenge);
  url.searchParams.set("code_challenge_method", "S256");
  url.searchParams.set("state", input.state);
  return url.toString();
}

/** 토큰 교환·갱신 응답 */
export interface SubscriptionTokenGrant {
  accessToken: string;
  /** 갱신 응답이 새 리프레시 토큰을 주지 않으면 null(기존 값을 유지한다) */
  refreshToken: string | null;
  /** 액세스 토큰 수명(초) */
  expiresIn: number;
}

export class SubscriptionAuthError extends Error {
  /** HTTP 오류면 상태 코드, 네트워크·모양 오류면 null */
  readonly status: number | null;
  readonly code: string;

  constructor(status: number | null, code: string, message: string) {
    super(message);
    this.name = "SubscriptionAuthError";
    this.status = status;
    this.code = code;
  }
}

/** 리프레시 토큰이 더는 쓸 수 없는 오류인가(폐기·만료·재사용). 이때는 로컬 자격증명을 지운다 */
export function isSubscriptionRefreshInvalid(error: unknown): boolean {
  if (!(error instanceof SubscriptionAuthError)) return false;
  return error.code === INVALID_GRANT || error.status === 401;
}

export interface SubscriptionExchangeInput {
  code: string;
  state: string;
  codeVerifier: string;
  redirectUri: string;
}

export interface SubscriptionTokenApi {
  exchange(input: SubscriptionExchangeInput, signal?: AbortSignal): Promise<SubscriptionTokenGrant>;
  refresh(refreshToken: string, signal?: AbortSignal): Promise<SubscriptionTokenGrant>;
}

export interface SubscriptionTokenApiOptions {
  fetch?: typeof fetch;
  timeoutMs?: number;
  tokenUrl?: string;
}

export function createSubscriptionTokenApi(options: SubscriptionTokenApiOptions = {}): SubscriptionTokenApi {
  const doFetch = options.fetch ?? fetch;
  const timeoutMs = options.timeoutMs ?? SUBSCRIPTION_AUTH_TIMEOUT_MS;
  const tokenUrl = options.tokenUrl ?? SUBSCRIPTION_TOKEN_URL;

  async function postForGrant(body: Record<string, string>, signal: AbortSignal | undefined): Promise<SubscriptionTokenGrant> {
    const signals = [AbortSignal.timeout(timeoutMs)];
    if (signal !== undefined) signals.push(signal);

    let response: Response;
    try {
      response = await doFetch(tokenUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify(body),
        signal: AbortSignal.any(signals),
      });
    } catch (error) {
      if (signal?.aborted === true && isAbortError(error)) throw error;
      logger.warn("[knot] Claude 인증 서버 호출 실패", { grant: body["grant_type"], reason: errorName(error) });
      throw new SubscriptionAuthError(
        null,
        SUBSCRIPTION_AUTH_ERRORS.unreachable.code,
        SUBSCRIPTION_AUTH_ERRORS.unreachable.message,
      );
    }

    if (!response.ok) {
      throw await toAuthError(response, body["grant_type"] ?? "");
    }

    let json: unknown;
    try {
      json = await response.json();
    } catch (error) {
      logger.warn("[knot] Claude 인증 응답 본문을 읽지 못했다", { reason: errorName(error) });
      throw malformed();
    }
    return parseGrant(json);
  }

  return {
    exchange(input, signal) {
      return postForGrant(
        {
          grant_type: "authorization_code",
          code: input.code,
          state: input.state,
          redirect_uri: input.redirectUri,
          client_id: SUBSCRIPTION_CLIENT_ID,
          code_verifier: input.codeVerifier,
        },
        signal,
      );
    },

    refresh(refreshToken, signal) {
      return postForGrant(
        { grant_type: "refresh_token", refresh_token: refreshToken, client_id: SUBSCRIPTION_CLIENT_ID },
        signal,
      );
    },
  };
}

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === "AbortError";
}

async function toAuthError(response: Response, grantType: string): Promise<SubscriptionAuthError> {
  let { code, message } = SUBSCRIPTION_AUTH_ERRORS.unknown as { code: string; message: string };
  try {
    const body = (await response.json()) as { error?: unknown; error_description?: unknown };
    if (typeof body.error === "string" && body.error.length > 0) {
      code = body.error;
      if (typeof body.error_description === "string" && body.error_description.length > 0) {
        message = body.error_description;
      }
    }
  } catch {
    // 본문이 비었거나 JSON이 아니다
  }
  logger.warn("[knot] Claude 인증 서버 HTTP 오류", { grant: grantType, status: response.status, code });
  return new SubscriptionAuthError(response.status, code, message);
}

function parseGrant(json: unknown): SubscriptionTokenGrant {
  if (typeof json !== "object" || json === null) throw malformed();
  const { access_token: accessToken, refresh_token: refreshToken, expires_in: expiresIn } = json as Record<
    string,
    unknown
  >;
  if (
    typeof accessToken !== "string" ||
    accessToken.length === 0 ||
    typeof expiresIn !== "number" ||
    !Number.isFinite(expiresIn) ||
    expiresIn <= 0
  ) {
    throw malformed();
  }
  if (refreshToken !== undefined && refreshToken !== null && typeof refreshToken !== "string") throw malformed();
  const refresh = typeof refreshToken === "string" && refreshToken.length > 0 ? refreshToken : null;
  return { accessToken, refreshToken: refresh, expiresIn };
}

function malformed(): SubscriptionAuthError {
  logger.warn("[knot] Claude 인증 응답의 모양이 계약과 다르다");
  return new SubscriptionAuthError(
    null,
    SUBSCRIPTION_AUTH_ERRORS.malformed.code,
    SUBSCRIPTION_AUTH_ERRORS.malformed.message,
  );
}

function errorName(error: unknown): string {
  return error instanceof Error ? error.name : "Unknown";
}
