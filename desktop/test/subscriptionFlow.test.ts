import { beforeEach, describe, expect, it, vi } from "vitest";
import type { LlmSubscriptionStatus } from "../src/shared/api";
import type { AuthCallbackParams } from "../src/main/auth/authorizeUrl";
import type { LoopbackServer } from "../src/main/auth/loopbackServer";
import type { SubscriptionCredentials, SubscriptionStore } from "../src/main/llm/subscriptionStore";
import { logMock } from "./helpers/logMock";

vi.mock("electron-log/main", () => ({ default: logMock }));

const { SubscriptionAuthError } = await import("../src/main/llm/subscriptionOAuth");
const { MIN_REFRESH_DELAY_MS, REFRESH_LEAD_MS, REFRESH_RETRY_DELAY_MS, SubscriptionFlowError, createSubscriptionController } =
  await import("../src/main/llm/subscriptionFlow");

const T0 = Date.parse("2026-09-09T12:00:00.000Z");
const GRANT = { accessToken: "sk-ant-oat01-SECRET-A1", refreshToken: "sk-ant-ort01-SECRET-R1", expiresIn: 28_800 };

interface FakeLoopback extends LoopbackServer {
  emit(params: AuthCallbackParams): void;
  closed: number;
}

function fakeLoopback(port = 4321): FakeLoopback {
  let resolveCallback: (params: AuthCallbackParams) => void = () => {};
  let rejectCallback: (error: Error) => void = () => {};
  const callback = new Promise<AuthCallbackParams>((resolve, reject) => {
    resolveCallback = resolve;
    rejectCallback = reject;
  });
  callback.catch(() => {});
  const server: FakeLoopback = {
    port,
    callback,
    closed: 0,
    close() {
      server.closed += 1;
      rejectCallback(new Error("closed"));
    },
    emit(params) {
      resolveCallback(params);
    },
  };
  return server;
}

interface Timer {
  id: number;
  callback: () => void;
  delayMs: number;
}

function harness(initial: SubscriptionCredentials | null = null) {
  let now = T0;
  const timers: Timer[] = [];
  let nextTimerId = 1;
  let reads = 0;
  let stored: SubscriptionCredentials | null = initial;
  const credentials: SubscriptionStore = {
    read() {
      reads += 1;
      return stored;
    },
    write(value) {
      stored = value;
    },
    clear() {
      stored = null;
    },
  };
  const loopbacks: FakeLoopback[] = [];
  const api = { exchange: vi.fn(), refresh: vi.fn() };
  const openExternal = vi.fn(async (_url: string) => {});
  const onStatusChanged = vi.fn((_status: LlmSubscriptionStatus) => {});

  const controller = createSubscriptionController({
    api,
    credentials,
    settings: { read: () => ({ model: "claude-fable-5-1", effort: "high" }) },
    startLoopback: async () => {
      const server = fakeLoopback();
      loopbacks.push(server);
      return server;
    },
    openExternal,
    onStatusChanged,
    pkce: () => ({ verifier: "SECRET-VERIFIER", challenge: "CHALLENGE" }),
    state: () => "SECRET-STATE",
    now: () => now,
    setTimer: (callback, delayMs) => {
      const timer = { id: nextTimerId++, callback, delayMs };
      timers.push(timer);
      return timer.id;
    },
    clearTimer: (handle) => {
      const index = timers.findIndex((timer) => timer.id === handle);
      if (index >= 0) timers.splice(index, 1);
    },
    signInTimeoutMs: 300_000,
  });

  return {
    controller,
    api,
    openExternal,
    onStatusChanged,
    timers,
    loopbacks,
    get stored() {
      return stored;
    },
    get reads() {
      return reads;
    },
    advance(ms: number) {
      now += ms;
    },
    /** 등록된 타이머 중 하나를 즉시 실행한다 */
    fire(predicate: (timer: Timer) => boolean) {
      const index = timers.findIndex(predicate);
      if (index < 0) throw new Error("타이머가 없다");
      const [timer] = timers.splice(index, 1);
      timer!.callback();
    },
  };
}

const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

beforeEach(() => {
  logMock.info.mockReset();
  logMock.warn.mockReset();
});

async function completeSignIn(h: ReturnType<typeof harness>) {
  h.api.exchange.mockResolvedValueOnce(GRANT);
  const signIn = h.controller.signIn();
  await flush();
  h.loopbacks[0]!.emit({ code: "SECRET-CODE", state: "SECRET-STATE" });
  await signIn;
}

describe("signIn", () => {
  it("claude.ai 인가 URL을 시스템 브라우저로 열고 콜백 코드를 교환해 자격증명을 저장한다(Q61 a·b)", async () => {
    const h = harness();

    await completeSignIn(h);

    const opened = new URL(h.openExternal.mock.calls[0]![0]);
    expect(opened.origin).toBe("https://claude.ai");
    expect(opened.searchParams.get("state")).toBe("SECRET-STATE");
    expect(opened.searchParams.get("code_challenge")).toBe("CHALLENGE");
    expect(opened.searchParams.get("redirect_uri")).toBe("http://localhost:4321/callback");
    expect(h.api.exchange).toHaveBeenCalledWith({
      code: "SECRET-CODE",
      state: "SECRET-STATE",
      codeVerifier: "SECRET-VERIFIER",
      redirectUri: "http://localhost:4321/callback",
    });
    expect(h.stored).toEqual({
      accessToken: GRANT.accessToken,
      refreshToken: GRANT.refreshToken,
      expiresAt: new Date(T0 + 28_800_000).toISOString(),
    });
    expect(h.loopbacks[0]!.closed).toBeGreaterThan(0);
    expect(h.controller.isSignInInProgress()).toBe(false);
  });

  it("성공하면 상태를 알리고 만료 5분 전 갱신을 예약한다", async () => {
    const h = harness();

    await completeSignIn(h);

    expect(h.onStatusChanged).toHaveBeenCalledTimes(1);
    expect(h.onStatusChanged.mock.calls[0]![0]).toEqual({
      signedIn: true,
      expiresAt: new Date(T0 + 28_800_000).toISOString(),
      model: "claude-fable-5-1",
      lastError: null,
      lastAnsweredBy: null,
    });
    expect(h.timers).toHaveLength(1);
    expect(h.timers[0]!.delayMs).toBe(28_800_000 - REFRESH_LEAD_MS);
  });

  it("state가 다른 콜백은 무시하고 계속 기다린다", async () => {
    const h = harness();
    h.api.exchange.mockResolvedValueOnce(GRANT);
    const signIn = h.controller.signIn();
    await flush();

    // 첫 loopback의 콜백은 한 번만 오므로 딥링크처럼 두 번째 콜백을 흉내 낼 수 없다.
    // 대신 위조 state로 첫 콜백을 보내면 무시되고 로그인은 대기 중이어야 한다
    h.loopbacks[0]!.emit({ code: "x", state: "WRONG" });
    await flush();

    expect(h.api.exchange).not.toHaveBeenCalled();
    expect(h.controller.isSignInInProgress()).toBe(true);
    h.controller.signOut();
    await expect(signIn).rejects.toBeInstanceOf(SubscriptionFlowError);
  });

  it("사용자가 거부하면 SIGN_IN_REJECTED로 끝나고 자격증명은 없다", async () => {
    const h = harness();
    const signIn = h.controller.signIn();
    await flush();

    // RFC 6749 §4.1.2.1: 거부 응답에도 요청의 state가 실린다. state 없는 error는 위조로 보고 무시한다
    h.loopbacks[0]!.emit({ error: "access_denied", state: "SECRET-STATE" });

    const error = (await signIn.catch((caught: unknown) => caught)) as InstanceType<typeof SubscriptionFlowError>;
    expect(error.code).toBe("SIGN_IN_REJECTED");
    expect(h.stored).toBeNull();
    expect(h.onStatusChanged).not.toHaveBeenCalled();
  });

  it("코드 없는 콜백은 INVALID_CALLBACK이다", async () => {
    const h = harness();
    const signIn = h.controller.signIn();
    await flush();

    h.loopbacks[0]!.emit({ state: "SECRET-STATE" });

    const error = (await signIn.catch((caught: unknown) => caught)) as InstanceType<typeof SubscriptionFlowError>;
    expect(error.code).toBe("INVALID_CALLBACK");
  });

  it("브라우저 대기 시간이 지나면 SIGN_IN_TIMEOUT으로 끝나고 loopback을 닫는다", async () => {
    const h = harness();
    const signIn = h.controller.signIn();
    await flush();

    h.fire((timer) => timer.delayMs === 300_000);

    const error = (await signIn.catch((caught: unknown) => caught)) as InstanceType<typeof SubscriptionFlowError>;
    expect(error.code).toBe("SIGN_IN_TIMEOUT");
    expect(h.loopbacks[0]!.closed).toBeGreaterThan(0);
  });

  it("브라우저를 못 열면 SIGN_IN_OPEN_FAILED다", async () => {
    const h = harness();
    h.openExternal.mockRejectedValueOnce(new Error("no browser"));

    const error = (await h.controller.signIn().catch((caught: unknown) => caught)) as InstanceType<
      typeof SubscriptionFlowError
    >;

    expect(error.code).toBe("SIGN_IN_OPEN_FAILED");
    expect(h.controller.isSignInInProgress()).toBe(false);
  });

  it("대기 중에 다시 부르면 같은 URL을 다시 열고 새 loopback을 만들지 않는다", async () => {
    const h = harness();
    h.api.exchange.mockResolvedValueOnce(GRANT);
    const first = h.controller.signIn();
    await flush();
    const second = h.controller.signIn();
    await flush();

    expect(h.loopbacks).toHaveLength(1);
    expect(h.openExternal).toHaveBeenCalledTimes(2);
    expect(h.openExternal.mock.calls[1]![0]).toBe(h.openExternal.mock.calls[0]![0]);

    h.loopbacks[0]!.emit({ code: "c", state: "SECRET-STATE" });
    await expect(Promise.all([first, second])).resolves.toBeDefined();
  });

  it("토큰 교환이 실패하면 그 오류로 끝나고 로그인 상태가 아니다", async () => {
    const h = harness();
    h.api.exchange.mockRejectedValueOnce(new SubscriptionAuthError(400, "invalid_grant", "코드가 만료됐어요"));
    const signIn = h.controller.signIn();
    await flush();

    h.loopbacks[0]!.emit({ code: "c", state: "SECRET-STATE" });

    const error = (await signIn.catch((caught: unknown) => caught)) as InstanceType<typeof SubscriptionAuthError>;
    expect(error.code).toBe("invalid_grant");
    expect(h.controller.getStatus().signedIn).toBe(false);
  });

  it("첫 교환 응답에 리프레시 토큰이 없으면 CLAUDE_OAUTH_MALFORMED로 끝난다", async () => {
    const h = harness();
    h.api.exchange.mockResolvedValueOnce({ ...GRANT, refreshToken: null });
    const signIn = h.controller.signIn();
    await flush();

    h.loopbacks[0]!.emit({ code: "c", state: "SECRET-STATE" });

    const error = (await signIn.catch((caught: unknown) => caught)) as InstanceType<typeof SubscriptionAuthError>;
    expect(error.code).toBe("CLAUDE_OAUTH_MALFORMED");
    expect(h.stored).toBeNull();
  });
});

describe("restore·갱신", () => {
  const STORED: SubscriptionCredentials = {
    accessToken: "sk-ant-oat01-SECRET-A0",
    refreshToken: "sk-ant-ort01-SECRET-R0",
    expiresAt: new Date(T0 + 3_600_000).toISOString(),
  };

  it("저장된 자격증명이 있으면 만료 5분 전 갱신을 예약하고, 때가 되면 리프레시 토큰으로 갱신한다", async () => {
    const h = harness(STORED);
    h.api.refresh.mockResolvedValueOnce({ accessToken: "sk-ant-oat01-SECRET-A1", refreshToken: null, expiresIn: 7_200 });

    h.controller.restore();
    expect(h.timers[0]!.delayMs).toBe(3_600_000 - REFRESH_LEAD_MS);

    h.advance(3_600_000 - REFRESH_LEAD_MS);
    h.fire(() => true);
    await flush();

    expect(h.api.refresh).toHaveBeenCalledWith("sk-ant-ort01-SECRET-R0");
    // 갱신 응답에 리프레시 토큰이 없으면 기존 값을 유지한다
    expect(h.stored).toEqual({
      accessToken: "sk-ant-oat01-SECRET-A1",
      refreshToken: "sk-ant-ort01-SECRET-R0",
      expiresAt: new Date(T0 + 3_600_000 - REFRESH_LEAD_MS + 7_200_000).toISOString(),
    });
    expect(h.onStatusChanged).toHaveBeenCalledTimes(1);
    expect(h.timers).toHaveLength(1);
  });

  it("만료가 임박한 자격증명은 시작하자마자 갱신한다", async () => {
    const h = harness({ ...STORED, expiresAt: new Date(T0 + 60_000).toISOString() });
    h.api.refresh.mockResolvedValueOnce(GRANT);

    h.controller.restore();
    await flush();

    expect(h.api.refresh).toHaveBeenCalledTimes(1);
    expect(h.stored?.accessToken).toBe(GRANT.accessToken);
  });

  it("리프레시 토큰이 무효(invalid_grant)면 자격증명을 지우고 로그아웃 상태를 알린다", async () => {
    const h = harness({ ...STORED, expiresAt: new Date(T0 + 60_000).toISOString() });
    h.api.refresh.mockRejectedValueOnce(new SubscriptionAuthError(400, "invalid_grant", "폐기됨"));

    h.controller.restore();
    await flush();

    expect(h.stored).toBeNull();
    expect(h.onStatusChanged.mock.calls[0]![0]).toMatchObject({ signedIn: false, expiresAt: null, lastError: "invalid_grant" });
    expect(h.timers).toHaveLength(0);
  });

  it("네트워크 오류면 자격증명을 지우지 않고 1분 뒤 다시 시도한다", async () => {
    const h = harness({ ...STORED, expiresAt: new Date(T0 + 60_000).toISOString() });
    h.api.refresh.mockRejectedValueOnce(new SubscriptionAuthError(null, "CLAUDE_OAUTH_UNREACHABLE", "연결 실패"));

    h.controller.restore();
    await flush();

    expect(h.stored).toEqual({ ...STORED, expiresAt: new Date(T0 + 60_000).toISOString() });
    expect(h.timers).toHaveLength(1);
    expect(h.timers[0]!.delayMs).toBe(REFRESH_RETRY_DELAY_MS);
    expect(h.controller.getStatus()).toMatchObject({ signedIn: true, lastError: "CLAUDE_OAUTH_UNREACHABLE" });
  });

  it("갱신 지연은 최소 10초다", () => {
    const h = harness({ ...STORED, expiresAt: new Date(T0 + REFRESH_LEAD_MS + 1_000).toISOString() });
    h.controller.restore();
    expect(h.timers[0]!.delayMs).toBe(MIN_REFRESH_DELAY_MS);
  });

  it("저장된 것이 없으면 아무것도 하지 않는다", () => {
    const h = harness();
    h.controller.restore();
    expect(h.timers).toHaveLength(0);
    expect(h.api.refresh).not.toHaveBeenCalled();
  });
});

describe("getAccessToken", () => {
  it("로그인돼 있지 않으면 null이다", async () => {
    expect(await harness().controller.getAccessToken()).toBeNull();
  });

  it("만료가 멀면 저장된 액세스 토큰을 그대로 준다", async () => {
    const h = harness({ accessToken: "a", refreshToken: "r", expiresAt: new Date(T0 + 3_600_000).toISOString() });
    expect(await h.controller.getAccessToken()).toBe("a");
    expect(h.api.refresh).not.toHaveBeenCalled();
  });

  it("만료가 임박하면 먼저 갱신하고 새 토큰을 준다", async () => {
    const h = harness({ accessToken: "a", refreshToken: "r", expiresAt: new Date(T0 + 60_000).toISOString() });
    h.api.refresh.mockResolvedValueOnce({ accessToken: "a2", refreshToken: "r2", expiresIn: 100 });

    expect(await h.controller.getAccessToken()).toBe("a2");
    expect(h.stored?.refreshToken).toBe("r2");
  });

  it("갱신이 무효로 실패하면 null이다", async () => {
    const h = harness({ accessToken: "a", refreshToken: "r", expiresAt: new Date(T0 + 60_000).toISOString() });
    h.api.refresh.mockRejectedValueOnce(new SubscriptionAuthError(401, "unauthorized", "x"));

    expect(await h.controller.getAccessToken()).toBeNull();
  });
});

describe("signOut·recordAnswer·dispose", () => {
  it("signOut은 자격증명·갱신 타이머를 지우고 대기 중인 로그인을 취소한다", async () => {
    const h = harness();
    await completeSignIn(h);
    const pending = h.controller.signIn();
    await flush();

    h.controller.signOut();

    expect(h.stored).toBeNull();
    expect(h.timers).toHaveLength(0);
    const error = (await pending.catch((caught: unknown) => caught)) as InstanceType<typeof SubscriptionFlowError>;
    expect(error.code).toBe("SIGN_IN_CANCELLED");
    expect(h.onStatusChanged.mock.lastCall![0]).toMatchObject({ signedIn: false, expiresAt: null, lastError: null });
  });

  it("recordAnswer는 마지막 응답 경로·오류를 상태에 반영하고 알린다", () => {
    const h = harness();

    h.controller.recordAnswer("server-sse", "SUBSCRIPTION_CREDITS_EXHAUSTED");

    expect(h.controller.getStatus()).toMatchObject({
      lastAnsweredBy: "server-sse",
      lastError: "SUBSCRIPTION_CREDITS_EXHAUSTED",
    });
    expect(h.onStatusChanged).toHaveBeenCalledTimes(1);
  });

  it("emitStatus는 현재 상태를 다시 알린다(L3 설정 변경용)", () => {
    const h = harness();

    h.controller.emitStatus();

    expect(h.onStatusChanged).toHaveBeenCalledTimes(1);
    expect(h.onStatusChanged.mock.calls[0]![0]).toMatchObject({ signedIn: false, model: "claude-fable-5-1" });
  });

  it("dispose는 대기 중인 로그인을 취소하고 타이머를 지운다", async () => {
    const h = harness();
    const pending = h.controller.signIn();
    await flush();

    h.controller.dispose();

    const error = (await pending.catch((caught: unknown) => caught)) as InstanceType<typeof SubscriptionFlowError>;
    expect(error.code).toBe("SIGN_IN_CANCELLED");
    expect(h.timers).toHaveLength(0);
  });
});

describe("토큰 누출 방지(GL 세 번째 항목)", () => {
  it("상태 객체·로그 어디에도 토큰·코드·verifier·state가 없고, 파일은 한 번만 읽는다(R23)", async () => {
    const h = harness({
      accessToken: "sk-ant-oat01-SECRET-A0",
      refreshToken: "sk-ant-ort01-SECRET-R0",
      expiresAt: new Date(T0 + 3_600_000).toISOString(),
    });
    h.controller.restore();
    h.controller.getStatus();
    h.controller.signOut();
    await completeSignIn(h);
    h.controller.getStatus();
    h.controller.getStatus();
    h.api.refresh.mockRejectedValueOnce(new SubscriptionAuthError(null, "CLAUDE_OAUTH_UNREACHABLE", "x"));
    h.fire(() => true);
    await flush();

    const statuses = JSON.stringify(h.onStatusChanged.mock.calls);
    const logs = JSON.stringify([...logMock.info.mock.calls, ...logMock.warn.mock.calls]);
    for (const text of [statuses, logs]) {
      expect(text).not.toContain("SECRET");
      expect(text).not.toContain("sk-ant-");
    }
    expect(Object.keys(h.controller.getStatus()).sort()).toEqual(
      ["expiresAt", "lastAnsweredBy", "lastError", "model", "signedIn"].sort(),
    );
    expect(h.reads).toBe(1);
  });
});
