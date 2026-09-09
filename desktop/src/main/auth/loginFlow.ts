/**
 * 시스템 브라우저 로그인·토큰 갱신·로그아웃 상태 기계 (기획서 5.2 시퀀스, 로드맵 `A7`).
 *
 * 흐름: verifier·state 생성 → loopback 열기 → 시스템 브라우저로 인가 URL → 콜백(`code`·`state`)
 * → state 대조 → `POST /auth/device/token`(PKCE) → access는 `auth.bin`, refresh는 `auth-session.bin`
 * → `signed-in`. 만료 5분 전에 백그라운드로 갱신하고, 리프레시 토큰이 무효(401)면 로컬 세션을 지우고
 * `signed-out`을 알린다. 네트워크 오류는 세션을 지우지 않고 1분 뒤 다시 시도한다.
 *
 * Electron을 import 하지 않는다 — 브라우저 열기·loopback·저장소·타이머를 전부 주입받아 vitest로 검증한다.
 * 접착은 `desktopAuth.ts`. 토큰·코드·verifier·state 값은 로그에 남기지 않는다.
 */

import { logger } from "../logging";
import { buildAuthorizeUrl } from "./authorizeUrl";
import type { AuthCallbackParams } from "./authorizeUrl";
import type { DeviceSessionStore } from "./deviceSessionStore";
import { isRefreshTokenInvalid } from "./deviceTokenApi";
import type { DeviceInfo, DeviceTokenApi, DeviceTokenGrant } from "./deviceTokenApi";
import type { LoopbackServer } from "./loopbackServer";
import { generatePkce, generateState } from "./pkce";
import type { PkcePair } from "./pkce";

export type SessionState = "signed-in" | "signed-out";

/** 브라우저에서 로그인을 마칠 때까지 기다리는 시간 */
export const LOGIN_TIMEOUT_MS = 5 * 60_000;
/** 만료 이 시간 전에 갱신한다(기획서 5.2 "만료 5분 전") */
export const REFRESH_LEAD_MS = 5 * 60_000;
/** 갱신 예약의 최소 지연. 이미 만료가 임박해도 잠깐은 기다린다 */
export const MIN_REFRESH_DELAY_MS = 10_000;
/** 갱신이 네트워크 오류로 실패했을 때 다시 시도하기까지의 시간 */
export const REFRESH_RETRY_DELAY_MS = 60_000;

export type AuthFlowErrorCode =
  | "LOGIN_TIMEOUT"
  | "LOGIN_CANCELLED"
  | "LOGIN_REJECTED"
  | "INVALID_CALLBACK"
  | "LOGIN_OPEN_FAILED";

export class AuthFlowError extends Error {
  readonly code: AuthFlowErrorCode;

  constructor(code: AuthFlowErrorCode, message: string) {
    super(message);
    this.name = "AuthFlowError";
    this.code = code;
  }
}

export interface AccessTokenStore {
  read(): string | null;
  write(token: string): void;
  clear(): void;
}

export interface AuthControllerDeps {
  apiOrigin: string;
  api: DeviceTokenApi;
  accessTokens: AccessTokenStore;
  sessions: DeviceSessionStore;
  startLoopback(): Promise<LoopbackServer>;
  /** 시스템 브라우저를 연다. 실패는 reject */
  openExternal(url: string): Promise<void>;
  device(): DeviceInfo;
  onSessionChanged(state: SessionState): void;
  /** 아래는 테스트용 주입점 */
  pkce?(): PkcePair;
  state?(): string;
  now?(): number;
  setTimer?(callback: () => void, delayMs: number): unknown;
  clearTimer?(handle: unknown): void;
  loginTimeoutMs?: number;
}

export interface AuthController {
  /** 로그인이 끝나면 resolve, 실패·취소·타임아웃은 `AuthFlowError`·`DeviceAuthError`로 reject */
  startLogin(): Promise<void>;
  /** 딥링크(`knot://auth/callback`) 경로의 콜백. 대기 중인 로그인이 없거나 state가 다르면 무시한다 */
  handleCallback(params: AuthCallbackParams): void;
  /** 서버 세션 폐기(실패해도 계속) + 로컬 토큰 삭제 + `signed-out` */
  logout(): Promise<void>;
  /** 앱 시작 시: 저장된 세션이 있으면 갱신을 예약하거나 바로 갱신한다 */
  restore(): void;
  /** 앱 종료 시: 대기 중인 로그인·타이머 정리 */
  dispose(): void;
  isLoginInProgress(): boolean;
}

interface PendingLogin {
  verifier: string;
  state: string;
  url: string;
  loopback: LoopbackServer;
  timer: unknown;
  exchanging: boolean;
  resolve(): void;
  reject(error: unknown): void;
}

export function createAuthController(deps: AuthControllerDeps): AuthController {
  const now = deps.now ?? (() => Date.now());
  const setTimer = deps.setTimer ?? ((callback, delayMs) => setTimeout(callback, delayMs));
  const clearTimer = deps.clearTimer ?? ((handle) => clearTimeout(handle as ReturnType<typeof setTimeout>));
  const makePkce = deps.pkce ?? generatePkce;
  const makeState = deps.state ?? generateState;
  const loginTimeoutMs = deps.loginTimeoutMs ?? LOGIN_TIMEOUT_MS;

  let pending: PendingLogin | null = null;
  let refreshTimer: unknown = null;
  let refreshing: Promise<void> | null = null;

  function clearRefreshTimer(): void {
    if (refreshTimer !== null) {
      clearTimer(refreshTimer);
      refreshTimer = null;
    }
  }

  function finish(login: PendingLogin, outcome: { ok: true } | { ok: false; error: unknown }): void {
    if (pending !== login) return;
    pending = null;
    clearTimer(login.timer);
    login.loopback.close();
    if (outcome.ok) {
      login.resolve();
    } else {
      login.reject(outcome.error);
    }
  }

  function storeGrant(grant: DeviceTokenGrant): void {
    const expiresAt = now() + grant.expiresIn * 1000;
    deps.accessTokens.write(grant.accessToken);
    deps.sessions.write({
      refreshToken: grant.refreshToken,
      sessionId: grant.session.id,
      deviceName: grant.session.deviceName,
      accessTokenExpiresAt: new Date(expiresAt).toISOString(),
    });
    scheduleRefresh(expiresAt);
  }

  function scheduleRefresh(expiresAtMs: number): void {
    clearRefreshTimer();
    const delay = Math.max(expiresAtMs - now() - REFRESH_LEAD_MS, MIN_REFRESH_DELAY_MS);
    refreshTimer = setTimer(() => {
      refreshTimer = null;
      void refreshNow();
    }, delay);
    logger.info("[knot] 액세스 토큰 갱신 예약", { inSeconds: Math.round(delay / 1000) });
  }

  function scheduleRetry(): void {
    clearRefreshTimer();
    refreshTimer = setTimer(() => {
      refreshTimer = null;
      void refreshNow();
    }, REFRESH_RETRY_DELAY_MS);
  }

  function signOutLocally(reason: string): void {
    clearRefreshTimer();
    deps.accessTokens.clear();
    deps.sessions.clear();
    logger.info("[knot] 로컬 세션 삭제", { reason });
    deps.onSessionChanged("signed-out");
  }

  function refreshNow(): Promise<void> {
    if (refreshing !== null) return refreshing;
    const session = deps.sessions.read();
    if (session === null) return Promise.resolve();

    refreshing = deps.api
      .refresh(session.refreshToken)
      .then(
        (grant) => {
          storeGrant(grant);
          logger.info("[knot] 액세스 토큰 갱신");
        },
        (error: unknown) => {
          if (isRefreshTokenInvalid(error)) {
            signOutLocally("리프레시 토큰 무효");
            return;
          }
          logger.warn("[knot] 액세스 토큰 갱신 실패, 다시 시도 예정", { reason: errorName(error) });
          scheduleRetry();
        },
      )
      .finally(() => {
        refreshing = null;
      });
    return refreshing;
  }

  function exchange(login: PendingLogin, code: string): void {
    login.exchanging = true;
    deps.api.exchange({ code, codeVerifier: login.verifier, device: deps.device() }).then(
      (grant) => {
        if (pending !== login) return;
        storeGrant(grant);
        logger.info("[knot] 브라우저 로그인 완료", { deviceName: grant.session.deviceName });
        finish(login, { ok: true });
        deps.onSessionChanged("signed-in");
      },
      (error: unknown) => {
        logger.warn("[knot] 토큰 교환 실패", { reason: errorName(error) });
        finish(login, { ok: false, error });
      },
    );
  }

  function handleCallback(params: AuthCallbackParams): void {
    const login = pending;
    if (login === null) {
      logger.warn("[knot] 대기 중인 로그인이 없는 콜백 무시");
      return;
    }
    if (login.exchanging) {
      logger.warn("[knot] 이미 교환 중인 로그인의 콜백 무시");
      return;
    }
    if (params.state !== login.state) {
      // 다른 로그인 시도의 콜백이거나 위조다. 대기 중인 로그인은 그대로 둔다(기획서 5.3)
      logger.warn("[knot] state가 다른 콜백 무시");
      return;
    }
    if (params.error !== undefined) {
      finish(login, { ok: false, error: new AuthFlowError("LOGIN_REJECTED", `로그인에 실패했어요(${params.error}).`) });
      return;
    }
    if (params.code === undefined) {
      finish(login, { ok: false, error: new AuthFlowError("INVALID_CALLBACK", "로그인 콜백에 코드가 없어요.") });
      return;
    }
    exchange(login, params.code);
  }

  async function startLogin(): Promise<void> {
    if (pending !== null) {
      // 브라우저 탭을 닫았을 수 있으니 같은 URL을 다시 연다. 새 시도를 만들지는 않는다
      const current = pending;
      await deps.openExternal(current.url);
      return new Promise((resolve, reject) => {
        const previousResolve = current.resolve;
        const previousReject = current.reject;
        current.resolve = () => {
          previousResolve();
          resolve();
        };
        current.reject = (error) => {
          previousReject(error);
          reject(error as Error);
        };
      });
    }

    const { verifier, challenge } = makePkce();
    const state = makeState();
    const loopback = await deps.startLoopback();
    const url = buildAuthorizeUrl({ apiOrigin: deps.apiOrigin, challenge, state, loopbackPort: loopback.port });

    return new Promise<void>((resolve, reject) => {
      const login: PendingLogin = {
        verifier,
        state,
        url,
        loopback,
        timer: null,
        exchanging: false,
        resolve,
        reject,
      };
      login.timer = setTimer(() => {
        finish(login, { ok: false, error: new AuthFlowError("LOGIN_TIMEOUT", "브라우저에서 로그인이 끝나지 않았어요.") });
      }, loginTimeoutMs);
      pending = login;

      loopback.callback.then(
        (params) => {
          if (pending === login) handleCallback(params);
        },
        () => {
          // finish()가 닫은 것이면 이미 정리됐다. 밖에서 닫혔으면 취소로 본다
          if (pending === login) {
            finish(login, { ok: false, error: new AuthFlowError("LOGIN_CANCELLED", "로그인이 취소됐어요.") });
          }
        },
      );

      logger.info("[knot] 브라우저 로그인 시작", { loopbackPort: loopback.port });
      deps.openExternal(url).catch((error: unknown) => {
        logger.warn("[knot] 브라우저 열기 실패", { reason: errorName(error) });
        finish(login, { ok: false, error: new AuthFlowError("LOGIN_OPEN_FAILED", "시스템 브라우저를 열지 못했어요.") });
      });
    });
  }

  async function logout(): Promise<void> {
    if (pending !== null) {
      finish(pending, { ok: false, error: new AuthFlowError("LOGIN_CANCELLED", "로그아웃으로 로그인이 취소됐어요.") });
    }
    const session = deps.sessions.read();
    if (session !== null) {
      try {
        await deps.api.revoke(session.refreshToken);
        logger.info("[knot] 기기 세션 폐기");
      } catch (error) {
        // RFC 7009: 서버가 못 받아도 로컬 삭제는 진행한다
        logger.warn("[knot] 기기 세션 폐기 실패, 로컬만 지운다", { reason: errorName(error) });
      }
    }
    signOutLocally("로그아웃");
  }

  function restore(): void {
    const session = deps.sessions.read();
    if (session === null) return;
    const expiresAt = Date.parse(session.accessTokenExpiresAt);
    const accessToken = deps.accessTokens.read();
    if (accessToken === null || Number.isNaN(expiresAt) || expiresAt - now() <= REFRESH_LEAD_MS) {
      logger.info("[knot] 저장된 기기 세션으로 액세스 토큰 갱신");
      void refreshNow();
      return;
    }
    scheduleRefresh(expiresAt);
  }

  function dispose(): void {
    if (pending !== null) {
      finish(pending, { ok: false, error: new AuthFlowError("LOGIN_CANCELLED", "앱이 종료돼 로그인이 취소됐어요.") });
    }
    clearRefreshTimer();
  }

  return {
    startLogin,
    handleCallback,
    logout,
    restore,
    dispose,
    isLoginInProgress: () => pending !== null,
  };
}

function errorName(error: unknown): string {
  return error instanceof Error ? error.name : "Unknown";
}
