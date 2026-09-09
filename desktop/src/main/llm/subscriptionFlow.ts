/**
 * 사용자 Claude 구독 로그인·토큰 갱신·로그아웃 상태 기계 (기획서 6.5, 로드맵 `L1`, Q61 a·b).
 *
 * 흐름: verifier·state 생성 → loopback 열기 → 시스템 브라우저로 `claude.ai/oauth/authorize` → 콜백(`code`·`state`)
 * → state 대조 → `platform.claude.com/v1/oauth/token`(PKCE) → `subscription-auth.bin` 저장 → 상태 변경 알림.
 * 만료 5분 전에 백그라운드로 갱신하고, 리프레시 토큰이 무효(`invalid_grant`·401)면 자격증명을 지우고
 * 로그아웃 상태를 알린다. 네트워크 오류는 자격증명을 지우지 않고 1분 뒤 다시 시도한다.
 *
 * `A7`의 `loginFlow`와 같은 모양이지만 대상이 다르다 — 여기서 얻는 것은 Knot 서버 세션이 아니라 사용자 본인의
 * Claude 구독 자격증명이며, `L2`가 `getAccessToken()`으로 꺼내 `api.anthropic.com`을 부른다.
 *
 * Electron을 import 하지 않는다 — 브라우저 열기·loopback·저장소·타이머를 전부 주입받아 vitest로 검증한다.
 * 접착은 `desktopLlm.ts`. 자격증명은 메모리에 한 번 올려 두고(상태 조회마다 복호화·로그를 만들지 않게, R23)
 * 파일과 함께 갱신한다. 토큰·코드·verifier·state 값은 상태 객체·로그 어디에도 싣지 않는다.
 */

import type { LlmSubscriptionStatus } from "../../shared/api";
import type { AuthCallbackParams } from "../auth/authorizeUrl";
import type { LoopbackServer } from "../auth/loopbackServer";
import { generatePkce, generateState } from "../auth/pkce";
import type { PkcePair } from "../auth/pkce";
import { logger } from "../logging";
import type { LlmSettingsStore } from "./llmSettings";
import {
  SUBSCRIPTION_AUTH_ERRORS,
  SubscriptionAuthError,
  buildSubscriptionAuthorizeUrl,
  buildSubscriptionRedirectUri,
  isSubscriptionRefreshInvalid,
} from "./subscriptionOAuth";
import type { SubscriptionTokenApi, SubscriptionTokenGrant } from "./subscriptionOAuth";
import type { SubscriptionCredentials, SubscriptionStore } from "./subscriptionStore";

/** 브라우저에서 구독 로그인을 마칠 때까지 기다리는 시간 */
export const SIGN_IN_TIMEOUT_MS = 5 * 60_000;
/** 만료 이 시간 전에 갱신한다(`A7`과 같은 값) */
export const REFRESH_LEAD_MS = 5 * 60_000;
/** 갱신 예약의 최소 지연 */
export const MIN_REFRESH_DELAY_MS = 10_000;
/** 갱신이 네트워크 오류로 실패했을 때 다시 시도하기까지의 시간 */
export const REFRESH_RETRY_DELAY_MS = 60_000;

export type SubscriptionFlowErrorCode =
  | "SIGN_IN_TIMEOUT"
  | "SIGN_IN_CANCELLED"
  | "SIGN_IN_REJECTED"
  | "INVALID_CALLBACK"
  | "SIGN_IN_OPEN_FAILED";

export class SubscriptionFlowError extends Error {
  readonly code: SubscriptionFlowErrorCode;

  constructor(code: SubscriptionFlowErrorCode, message: string) {
    super(message);
    this.name = "SubscriptionFlowError";
    this.code = code;
  }
}

export type AnsweredBy = NonNullable<LlmSubscriptionStatus["lastAnsweredBy"]>;

export interface SubscriptionControllerDeps {
  api: SubscriptionTokenApi;
  credentials: SubscriptionStore;
  settings: Pick<LlmSettingsStore, "read">;
  startLoopback(): Promise<LoopbackServer>;
  /** 시스템 브라우저를 연다. 실패는 reject */
  openExternal(url: string): Promise<void>;
  onStatusChanged(status: LlmSubscriptionStatus): void;
  /** 아래는 테스트용 주입점 */
  pkce?(): PkcePair;
  state?(): string;
  now?(): number;
  setTimer?(callback: () => void, delayMs: number): unknown;
  clearTimer?(handle: unknown): void;
  signInTimeoutMs?: number;
}

export interface SubscriptionController {
  /** 로그인이 끝나면 resolve, 실패·취소·타임아웃은 `SubscriptionFlowError`·`SubscriptionAuthError`로 reject */
  signIn(): Promise<void>;
  /** 로컬 자격증명 삭제 + 상태 알림. Anthropic 세션 자체는 폐기하지 않는다(claude.ai에서 관리) */
  signOut(): void;
  /** renderer에 주는 상태. 토큰 값은 담기지 않는다 */
  getStatus(): LlmSubscriptionStatus;
  /** `L2`용: 유효한 액세스 토큰. 만료가 임박하면 먼저 갱신한다. 로그인돼 있지 않으면 null */
  getAccessToken(): Promise<string | null>;
  /** `L2`용: 마지막 질문이 어느 경로로 응답됐는지와 오류 코드를 상태에 반영한다 */
  recordAnswer(by: AnsweredBy, errorCode: string | null): void;
  /** `L3`용: 설정(모델)이 바뀌었을 때 현재 상태를 다시 알린다 */
  emitStatus(): void;
  /** 앱 시작 시: 저장된 자격증명이 있으면 갱신을 예약하거나 바로 갱신한다 */
  restore(): void;
  /** 앱 종료 시: 대기 중인 로그인·타이머 정리 */
  dispose(): void;
  isSignInInProgress(): boolean;
}

interface PendingSignIn {
  verifier: string;
  state: string;
  url: string;
  redirectUri: string;
  loopback: LoopbackServer;
  timer: unknown;
  exchanging: boolean;
  resolve(): void;
  reject(error: unknown): void;
}

export function createSubscriptionController(deps: SubscriptionControllerDeps): SubscriptionController {
  const now = deps.now ?? (() => Date.now());
  const setTimer = deps.setTimer ?? ((callback, delayMs) => setTimeout(callback, delayMs));
  const clearTimer = deps.clearTimer ?? ((handle) => clearTimeout(handle as ReturnType<typeof setTimeout>));
  const makePkce = deps.pkce ?? generatePkce;
  const makeState = deps.state ?? generateState;
  const signInTimeoutMs = deps.signInTimeoutMs ?? SIGN_IN_TIMEOUT_MS;

  /** undefined면 아직 파일을 읽지 않았다 */
  let cached: SubscriptionCredentials | null | undefined;
  let pending: PendingSignIn | null = null;
  let refreshTimer: unknown = null;
  let refreshing: Promise<void> | null = null;
  let lastError: string | null = null;
  let lastAnsweredBy: AnsweredBy | null = null;

  function load(): SubscriptionCredentials | null {
    if (cached === undefined) cached = deps.credentials.read();
    return cached;
  }

  function getStatus(): LlmSubscriptionStatus {
    const credentials = load();
    return {
      signedIn: credentials !== null,
      expiresAt: credentials?.expiresAt ?? null,
      model: deps.settings.read().model,
      lastError,
      lastAnsweredBy,
    };
  }

  function emit(): void {
    deps.onStatusChanged(getStatus());
  }

  function clearRefreshTimer(): void {
    if (refreshTimer !== null) {
      clearTimer(refreshTimer);
      refreshTimer = null;
    }
  }

  function finish(signIn: PendingSignIn, outcome: { ok: true } | { ok: false; error: unknown }): void {
    if (pending !== signIn) return;
    pending = null;
    clearTimer(signIn.timer);
    signIn.loopback.close();
    if (outcome.ok) {
      signIn.resolve();
    } else {
      signIn.reject(outcome.error);
    }
  }

  /** 응답에 리프레시 토큰이 없으면 기존 값을 유지한다(갱신 응답). 처음 교환에는 반드시 있어야 한다 */
  function storeGrant(grant: SubscriptionTokenGrant, previousRefreshToken: string | null): void {
    const refreshToken = grant.refreshToken ?? previousRefreshToken;
    if (refreshToken === null) {
      throw new SubscriptionAuthError(
        null,
        SUBSCRIPTION_AUTH_ERRORS.malformed.code,
        SUBSCRIPTION_AUTH_ERRORS.malformed.message,
      );
    }
    const expiresAt = now() + grant.expiresIn * 1000;
    const credentials: SubscriptionCredentials = {
      accessToken: grant.accessToken,
      refreshToken,
      expiresAt: new Date(expiresAt).toISOString(),
    };
    deps.credentials.write(credentials);
    cached = credentials;
    scheduleRefresh(expiresAt);
  }

  function scheduleRefresh(expiresAtMs: number): void {
    clearRefreshTimer();
    const delay = Math.max(expiresAtMs - now() - REFRESH_LEAD_MS, MIN_REFRESH_DELAY_MS);
    refreshTimer = setTimer(() => {
      refreshTimer = null;
      void refreshNow();
    }, delay);
    logger.info("[knot] 구독 토큰 갱신 예약", { inSeconds: Math.round(delay / 1000) });
  }

  function scheduleRetry(): void {
    clearRefreshTimer();
    refreshTimer = setTimer(() => {
      refreshTimer = null;
      void refreshNow();
    }, REFRESH_RETRY_DELAY_MS);
  }

  function clearCredentials(reason: string): void {
    clearRefreshTimer();
    deps.credentials.clear();
    cached = null;
    logger.info("[knot] 구독 자격증명 삭제", { reason });
  }

  function refreshNow(): Promise<void> {
    if (refreshing !== null) return refreshing;
    const credentials = load();
    if (credentials === null) return Promise.resolve();

    refreshing = deps.api
      .refresh(credentials.refreshToken)
      .then(
        (grant) => {
          storeGrant(grant, credentials.refreshToken);
          lastError = null;
          logger.info("[knot] 구독 토큰 갱신");
          emit();
        },
        (error: unknown) => {
          lastError = errorCode(error);
          if (isSubscriptionRefreshInvalid(error)) {
            clearCredentials("리프레시 토큰 무효");
            emit();
            return;
          }
          logger.warn("[knot] 구독 토큰 갱신 실패, 다시 시도 예정", { reason: errorName(error) });
          scheduleRetry();
          emit();
        },
      )
      .finally(() => {
        refreshing = null;
      });
    return refreshing;
  }

  function exchange(signIn: PendingSignIn, code: string): void {
    signIn.exchanging = true;
    deps.api
      .exchange({ code, state: signIn.state, codeVerifier: signIn.verifier, redirectUri: signIn.redirectUri })
      .then(
        (grant) => {
          if (pending !== signIn) return;
          try {
            storeGrant(grant, null);
          } catch (error) {
            finish(signIn, { ok: false, error });
            return;
          }
          lastError = null;
          logger.info("[knot] 구독 로그인 완료", { expiresInSeconds: grant.expiresIn });
          finish(signIn, { ok: true });
          emit();
        },
        (error: unknown) => {
          logger.warn("[knot] 구독 토큰 교환 실패", { reason: errorName(error) });
          finish(signIn, { ok: false, error });
        },
      );
  }

  function handleCallback(params: AuthCallbackParams): void {
    const signIn = pending;
    if (signIn === null) {
      logger.warn("[knot] 대기 중인 구독 로그인이 없는 콜백 무시");
      return;
    }
    if (signIn.exchanging) {
      logger.warn("[knot] 이미 교환 중인 구독 로그인의 콜백 무시");
      return;
    }
    if (params.state !== signIn.state) {
      // 다른 로그인 시도의 콜백이거나 위조다. 대기 중인 로그인은 그대로 둔다
      logger.warn("[knot] state가 다른 구독 콜백 무시");
      return;
    }
    if (params.error !== undefined) {
      finish(signIn, {
        ok: false,
        error: new SubscriptionFlowError("SIGN_IN_REJECTED", `Claude 로그인에 실패했어요(${params.error}).`),
      });
      return;
    }
    if (params.code === undefined) {
      finish(signIn, {
        ok: false,
        error: new SubscriptionFlowError("INVALID_CALLBACK", "Claude 로그인 콜백에 코드가 없어요."),
      });
      return;
    }
    exchange(signIn, params.code);
  }

  async function signIn(): Promise<void> {
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
    const redirectUri = buildSubscriptionRedirectUri(loopback.port);
    const url = buildSubscriptionAuthorizeUrl({ challenge, state, redirectUri });

    return new Promise<void>((resolve, reject) => {
      const attempt: PendingSignIn = {
        verifier,
        state,
        url,
        redirectUri,
        loopback,
        timer: null,
        exchanging: false,
        resolve,
        reject,
      };
      attempt.timer = setTimer(() => {
        finish(attempt, {
          ok: false,
          error: new SubscriptionFlowError("SIGN_IN_TIMEOUT", "브라우저에서 Claude 로그인이 끝나지 않았어요."),
        });
      }, signInTimeoutMs);
      pending = attempt;

      loopback.callback.then(
        (params) => {
          if (pending === attempt) handleCallback(params);
        },
        () => {
          // finish()가 닫은 것이면 이미 정리됐다. 밖에서 닫혔으면 취소로 본다
          if (pending === attempt) {
            finish(attempt, {
              ok: false,
              error: new SubscriptionFlowError("SIGN_IN_CANCELLED", "Claude 로그인이 취소됐어요."),
            });
          }
        },
      );

      logger.info("[knot] 구독 로그인 시작", { loopbackPort: loopback.port });
      deps.openExternal(url).catch((error: unknown) => {
        logger.warn("[knot] 브라우저 열기 실패", { reason: errorName(error) });
        finish(attempt, {
          ok: false,
          error: new SubscriptionFlowError("SIGN_IN_OPEN_FAILED", "시스템 브라우저를 열지 못했어요."),
        });
      });
    });
  }

  function signOut(): void {
    if (pending !== null) {
      finish(pending, {
        ok: false,
        error: new SubscriptionFlowError("SIGN_IN_CANCELLED", "로그아웃으로 Claude 로그인이 취소됐어요."),
      });
    }
    clearCredentials("구독 로그아웃");
    lastError = null;
    emit();
  }

  async function getAccessToken(): Promise<string | null> {
    const credentials = load();
    if (credentials === null) return null;
    if (Date.parse(credentials.expiresAt) - now() <= REFRESH_LEAD_MS) {
      await refreshNow();
    }
    return load()?.accessToken ?? null;
  }

  function recordAnswer(by: AnsweredBy, errorCode: string | null): void {
    lastAnsweredBy = by;
    lastError = errorCode;
    emit();
  }

  function restore(): void {
    const credentials = load();
    if (credentials === null) return;
    const expiresAt = Date.parse(credentials.expiresAt);
    if (Number.isNaN(expiresAt) || expiresAt - now() <= REFRESH_LEAD_MS) {
      logger.info("[knot] 저장된 구독 자격증명으로 액세스 토큰 갱신");
      void refreshNow();
      return;
    }
    scheduleRefresh(expiresAt);
  }

  function dispose(): void {
    if (pending !== null) {
      finish(pending, {
        ok: false,
        error: new SubscriptionFlowError("SIGN_IN_CANCELLED", "앱이 종료돼 Claude 로그인이 취소됐어요."),
      });
    }
    clearRefreshTimer();
  }

  return {
    signIn,
    signOut,
    getStatus,
    getAccessToken,
    recordAnswer,
    emitStatus: emit,
    restore,
    dispose,
    isSignInInProgress: () => pending !== null,
  };
}

function errorCode(error: unknown): string {
  if (error instanceof SubscriptionAuthError) return error.code;
  return SUBSCRIPTION_AUTH_ERRORS.unknown.code;
}

function errorName(error: unknown): string {
  return error instanceof Error ? error.name : "Unknown";
}
