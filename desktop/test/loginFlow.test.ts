import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AuthCallbackParams } from "../src/main/auth/authorizeUrl";
import type { DeviceSession } from "../src/main/auth/deviceSessionStore";
import type { LoginPage, LoginPageRequest } from "../src/main/auth/loginFlow";
import { logMock } from "./helpers/logMock";

vi.mock("electron", () => ({ app: { getPath: () => "" } }));
vi.mock("electron-log/main", () => ({ default: logMock }));

const { AuthFlowError, createAuthController } = await import("../src/main/auth/loginFlow");
const { LoopbackClosedError } = await import("../src/main/auth/loopbackServer");

const API_ORIGIN = "https://dev-api.knoted.kr";
const PORT = 43111;
const LOGIN_TIMEOUT = 5000;

const GRANT = {
  accessToken: "access",
  refreshToken: "refresh",
  expiresIn: 3600,
  session: { id: "sid", deviceName: "mac" },
};

/** loopback 서버 대역. `deliver`로 콜백을 흉내 낸다 */
function fakeLoopback() {
  let resolveCallback: (params: AuthCallbackParams) => void = () => {};
  let rejectCallback: (error: Error) => void = () => {};
  const callback = new Promise<AuthCallbackParams>((resolve, reject) => {
    resolveCallback = resolve;
    rejectCallback = reject;
  });
  callback.catch(() => {});
  return {
    port: PORT,
    callback,
    close: vi.fn(() => {
      rejectCallback(new LoopbackClosedError());
    }),
    deliver: (params: AuthCallbackParams) => {
      resolveCallback(params);
    },
  };
}

function fakePage() {
  return { focus: vi.fn<() => void>(), close: vi.fn<() => void>() };
}

interface FakeTimer {
  callback: () => void;
  delayMs: number;
}

/** 상태 기계에 대역을 전부 꽂는다. 마지막 뷰 요청·열어 준 뷰·타이머를 기억한다 */
function makeHarness() {
  const timers: FakeTimer[] = [];
  const loopback = fakeLoopback();
  const page = fakePage();
  const requests: LoginPageRequest[] = [];
  const openLoginPage = vi.fn((request: LoginPageRequest): Promise<LoginPage> => {
    requests.push(request);
    return Promise.resolve(page);
  });
  const api = {
    exchange: vi.fn(() => Promise.resolve(GRANT)),
    refresh: vi.fn(() => Promise.resolve(GRANT)),
    revoke: vi.fn(() => Promise.resolve()),
  };
  let storedSession: DeviceSession | null = null;
  const accessTokens = { read: vi.fn(() => null), write: vi.fn(), clear: vi.fn() };
  const sessions = {
    read: () => storedSession,
    write: vi.fn((session: DeviceSession) => {
      storedSession = session;
    }),
    clear: vi.fn(() => {
      storedSession = null;
    }),
  };
  const onSessionChanged = vi.fn();
  const controller = createAuthController({
    apiOrigin: API_ORIGIN,
    api,
    accessTokens,
    sessions,
    startLoopback: () => Promise.resolve(loopback),
    openLoginPage,
    device: () => ({ name: "mac", platform: "darwin", appVersion: "0.0.0" }),
    onSessionChanged,
    pkce: () => ({ verifier: "verifier", challenge: "challenge" }),
    state: () => "state-1",
    now: () => 1_000_000,
    setTimer: (callback, delayMs) => {
      const handle: FakeTimer = { callback, delayMs };
      timers.push(handle);
      return handle;
    },
    clearTimer: (handle) => {
      const index = timers.indexOf(handle as FakeTimer);
      if (index >= 0) timers.splice(index, 1);
    },
    loginTimeoutMs: LOGIN_TIMEOUT,
  });
  const request = (): LoginPageRequest => {
    const first = requests[0];
    if (first === undefined) throw new Error("로그인 뷰 요청이 없다");
    return first;
  };
  const fireTimeout = (): void => {
    const timeout = timers.find((timer) => timer.delayMs === LOGIN_TIMEOUT);
    if (timeout === undefined) throw new Error("타임아웃 타이머가 없다");
    timeout.callback();
  };
  return { controller, loopback, page, request, fireTimeout, openLoginPage, api, accessTokens, onSessionChanged };
}

/** 마이크로태스크를 비운다 — openLoginPage의 then과 loopback.callback의 then이 돌게 */
async function flush(): Promise<void> {
  for (let i = 0; i < 5; i += 1) await Promise.resolve();
}

// 2026-09-10 Q68 재개정. 인가 URL은 창(시스템 브라우저·자식 창)이 아니라 메인 창 안 로그인
// 뷰에서 열고, loopback 콜백 오리진은 그 뷰에만 한시 허용해야 하므로 뷰 요청에 함께 싣는다.
describe("createAuthController — 메인 창 안 로그인 뷰", () => {
  let h: ReturnType<typeof makeHarness>;

  beforeEach(() => {
    h = makeHarness();
  });

  it("인가 URL(API 오리진)과 loopback 콜백 오리진으로 로그인 뷰를 연다", async () => {
    const login = h.controller.startLogin();
    await flush();

    expect(h.openLoginPage).toHaveBeenCalledTimes(1);
    const url = new URL(h.request().url);
    expect(url.origin).toBe(API_ORIGIN);
    expect(url.pathname).toBe("/oauth2/authorization/github");
    expect(url.searchParams.get("client")).toBe("desktop");
    expect(url.searchParams.get("return")).toBe(`loopback:${PORT}`);
    expect(h.request().callbackOrigin).toBe(`http://127.0.0.1:${PORT}`);

    h.loopback.deliver({ code: "dc", state: "state-1" });
    await expect(login).resolves.toBeUndefined();
  });

  it("콜백을 받으면 뷰를 바로 떼고 토큰을 교환해 signed-in을 알린다", async () => {
    const login = h.controller.startLogin();
    await flush();

    h.loopback.deliver({ code: "dc", state: "state-1" });
    await login;

    expect(h.page.close).toHaveBeenCalledTimes(1);
    expect(h.api.exchange).toHaveBeenCalledWith(
      expect.objectContaining({ code: "dc", codeVerifier: "verifier" }),
    );
    expect(h.accessTokens.write).toHaveBeenCalledWith("access");
    expect(h.onSessionChanged).toHaveBeenCalledWith("signed-in");
    expect(h.loopback.close).toHaveBeenCalled();
    expect(h.controller.isLoginInProgress()).toBe(false);
  });

  it("사용자가 뷰에서 취소하면 LOGIN_CANCELLED로 끝나고 loopback도 닫는다", async () => {
    const login = h.controller.startLogin();
    await flush();

    h.request().onCancelled();

    await expect(login).rejects.toMatchObject({ name: "AuthFlowError", code: "LOGIN_CANCELLED" });
    expect(h.loopback.close).toHaveBeenCalled();
    expect(h.api.exchange).not.toHaveBeenCalled();
    expect(h.onSessionChanged).not.toHaveBeenCalled();
    expect(h.controller.isLoginInProgress()).toBe(false);
  });

  // 2026-09-10. 헤더 띠의 "취소" 버튼은 IPC로 여기까지 온다(기획서 5.2)
  it("cancelLogin()이면 뷰를 떼고 LOGIN_CANCELLED로 끝난다", async () => {
    const login = h.controller.startLogin();
    await flush();

    h.controller.cancelLogin();

    await expect(login).rejects.toMatchObject({ name: "AuthFlowError", code: "LOGIN_CANCELLED" });
    expect(h.page.close).toHaveBeenCalledTimes(1);
    expect(h.loopback.close).toHaveBeenCalled();
    expect(h.controller.isLoginInProgress()).toBe(false);
  });

  it("대기 중인 로그인이 없으면 cancelLogin()은 아무 일도 하지 않는다", () => {
    expect(() => {
      h.controller.cancelLogin();
    }).not.toThrow();
    expect(h.page.close).not.toHaveBeenCalled();
  });

  it("콜백 뒤 교환 중에 취소가 들어와도 로그인은 취소되지 않는다", async () => {
    let resolveExchange: (grant: typeof GRANT) => void = () => {};
    h.api.exchange.mockImplementation(
      () =>
        new Promise<typeof GRANT>((resolve) => {
          resolveExchange = resolve;
        }),
    );
    const login = h.controller.startLogin();
    await flush();

    h.loopback.deliver({ code: "dc", state: "state-1" });
    await flush();
    h.request().onCancelled();
    resolveExchange(GRANT);

    await expect(login).resolves.toBeUndefined();
    expect(h.onSessionChanged).toHaveBeenCalledWith("signed-in");
  });

  it("로그인 중에 다시 부르면 뷰를 새로 만들지 않고 열린 뷰에 포커스를 준다", async () => {
    const first = h.controller.startLogin();
    await flush();
    const second = h.controller.startLogin();
    await flush();

    expect(h.openLoginPage).toHaveBeenCalledTimes(1);
    expect(h.page.focus).toHaveBeenCalledTimes(1);

    h.loopback.deliver({ code: "dc", state: "state-1" });
    await expect(first).resolves.toBeUndefined();
    await expect(second).resolves.toBeUndefined();
  });

  it("시간이 다 되면 뷰를 떼고 LOGIN_TIMEOUT으로 끝난다", async () => {
    const login = h.controller.startLogin();
    await flush();

    h.fireTimeout();

    await expect(login).rejects.toMatchObject({ code: "LOGIN_TIMEOUT" });
    expect(h.page.close).toHaveBeenCalledTimes(1);
    expect(h.loopback.close).toHaveBeenCalled();
  });

  it("뷰가 붙기 전에 흐름이 끝났으면 뒤늦게 붙은 뷰를 바로 뗀다", async () => {
    let resolvePage: (page: LoginPage) => void = () => {};
    h.openLoginPage.mockImplementation(
      () =>
        new Promise<LoginPage>((resolve) => {
          resolvePage = resolve;
        }),
    );
    const login = h.controller.startLogin();
    await flush();

    h.fireTimeout();
    await expect(login).rejects.toMatchObject({ code: "LOGIN_TIMEOUT" });

    const late = fakePage();
    resolvePage(late);
    await flush();
    expect(late.close).toHaveBeenCalledTimes(1);
  });

  it("뷰를 붙이지 못하면 LOGIN_OPEN_FAILED로 끝난다", async () => {
    h.openLoginPage.mockImplementation(() => Promise.reject(new Error("메인 창 없음")));
    const login = h.controller.startLogin();

    await expect(login).rejects.toMatchObject({ code: "LOGIN_OPEN_FAILED" });
    expect(h.loopback.close).toHaveBeenCalled();
    expect(h.controller.isLoginInProgress()).toBe(false);
  });

  it("실패 콜백(`error`)이면 뷰를 떼고 LOGIN_REJECTED로 끝난다", async () => {
    const login = h.controller.startLogin();
    await flush();

    // state 없는 실패 콜백은 state 대조에서 걸려 뷰가 유지된다 — 진짜 실패는 state를 함께 보낸다
    h.loopback.deliver({ error: "access_denied" });
    await flush();
    expect(h.controller.isLoginInProgress()).toBe(true);
    expect(h.page.close).not.toHaveBeenCalled();

    h.controller.handleCallback({ error: "access_denied", state: "state-1" });
    await expect(login).rejects.toMatchObject({ code: "LOGIN_REJECTED" });
    expect(h.page.close).toHaveBeenCalledTimes(1);
  });

  it("로그아웃하면 대기 중인 로그인 뷰를 떼고 취소한다", async () => {
    const login = h.controller.startLogin();
    await flush();

    await h.controller.logout();

    await expect(login).rejects.toBeInstanceOf(AuthFlowError);
    expect(h.page.close).toHaveBeenCalledTimes(1);
    expect(h.onSessionChanged).toHaveBeenCalledWith("signed-out");
  });
});
