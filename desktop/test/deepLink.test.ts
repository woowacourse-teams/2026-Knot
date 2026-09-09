import { beforeEach, describe, expect, it, vi } from "vitest";
import { logMock } from "./helpers/logMock";

const appMock = {
  on: vi.fn(),
  setAsDefaultProtocolClient: vi.fn(() => true),
};

vi.mock("electron", () => ({ app: appMock }));
vi.mock("electron-log/main", () => ({ default: logMock }));

const {
  createDeepLinkRouter,
  dispatchDeepLink,
  findDeepLinkArg,
  isKnotDeepLink,
  parseDeepLink,
  attachDeepLinkHandling,
  registerProtocolClient,
} = await import("../src/main/deepLink");
const { IPC_CHANNELS } = await import("../src/shared/api");

interface FakeWindow {
  isMinimized: ReturnType<typeof vi.fn>;
  isVisible: ReturnType<typeof vi.fn>;
  restore: ReturnType<typeof vi.fn>;
  show: ReturnType<typeof vi.fn>;
  focus: ReturnType<typeof vi.fn>;
  webContents: { isLoading: ReturnType<typeof vi.fn>; send: ReturnType<typeof vi.fn> };
}

function fakeWindow(loading = false): FakeWindow {
  return {
    isMinimized: vi.fn(() => false),
    isVisible: vi.fn(() => true),
    restore: vi.fn(),
    show: vi.fn(),
    focus: vi.fn(),
    webContents: { isLoading: vi.fn(() => loading), send: vi.fn() },
  };
}

// 문법은 기획서 4.3이 정본이다. 존재 여부는 SPA·서버가 판단하므로 여기서는 모양만 본다.
describe("parseDeepLink", () => {
  it("초대 링크를 파싱한다", () => {
    expect(parseDeepLink("knot://invite/abc-DEF_123")).toEqual({ type: "invite", token: "abc-DEF_123" });
  });

  it("채팅 링크는 세션이 없어도, 있어도 된다", () => {
    expect(parseDeepLink("knot://chat/12")).toEqual({ type: "chat", workspaceId: "12" });
    expect(parseDeepLink("knot://chat/12/345")).toEqual({ type: "chat", workspaceId: "12", sessionId: "345" });
  });

  it("호스트 없이 슬래시 셋으로 와도 같은 결과다(Windows 인자 형태)", () => {
    expect(parseDeepLink("knot:///invite/tok")).toEqual({ type: "invite", token: "tok" });
    expect(parseDeepLink("knot://chat/12/")).toEqual({ type: "chat", workspaceId: "12" });
  });

  it("로그인 콜백은 auth 타입으로 코드·state·error를 담는다", () => {
    expect(parseDeepLink("knot://auth/callback?code=dc&state=s")).toEqual({
      type: "auth",
      code: "dc",
      state: "s",
      error: null,
    });
    expect(parseDeepLink("knot://auth/callback?error=access_denied")).toEqual({
      type: "auth",
      code: null,
      state: null,
      error: "access_denied",
    });
  });

  it("다른 스킴·알 수 없는 경로·자격증명이 든 URL은 거부한다", () => {
    expect(parseDeepLink("https://knoted.kr/invite/x")).toBeNull();
    expect(parseDeepLink("knot://settings")).toBeNull();
    expect(parseDeepLink("knot://auth/other")).toBeNull();
    expect(parseDeepLink("knot://user:pw@invite/x")).toBeNull();
    expect(parseDeepLink("")).toBeNull();
    expect(parseDeepLink("not a url")).toBeNull();
  });

  it("토큰·ID 문법이 어긋나면 거부한다", () => {
    expect(parseDeepLink("knot://invite/")).toBeNull();
    expect(parseDeepLink("knot://invite/a/b")).toBeNull();
    expect(parseDeepLink("knot://invite/%2e%2e")).toBeNull();
    expect(parseDeepLink("knot://chat/0")).toBeNull();
    expect(parseDeepLink("knot://chat/abc")).toBeNull();
    expect(parseDeepLink("knot://chat/1/2/3")).toBeNull();
    expect(parseDeepLink("knot://chat/1/x")).toBeNull();
    expect(parseDeepLink(`knot://invite/${"a".repeat(3000)}`)).toBeNull();
  });
});

describe("isKnotDeepLink", () => {
  it("계약 모양만 통과시킨다", () => {
    expect(isKnotDeepLink({ type: "invite", token: "t" })).toBe(true);
    expect(isKnotDeepLink({ type: "chat", workspaceId: "1" })).toBe(true);
    expect(isKnotDeepLink({ type: "chat", workspaceId: "1", sessionId: "2" })).toBe(true);
    expect(isKnotDeepLink({ type: "chat", workspaceId: 1 })).toBe(false);
    expect(isKnotDeepLink({ type: "auth", code: "x" })).toBe(false);
    expect(isKnotDeepLink(null)).toBe(false);
    expect(isKnotDeepLink("knot://invite/t")).toBe(false);
  });
});

describe("findDeepLinkArg", () => {
  it("실행 인자에서 마지막 knot:// 인자를 고른다", () => {
    expect(findDeepLinkArg(["Knot.exe", "--flag", "knot://invite/a", "knot://chat/1"])).toBe("knot://chat/1");
    expect(findDeepLinkArg(["Knot.exe"])).toBeNull();
  });
});

describe("dispatchDeepLink", () => {
  it("창을 앞으로 가져오고 knot:deep-link 채널로 보낸다", () => {
    const window = fakeWindow();
    window.isMinimized.mockReturnValue(true);
    window.isVisible.mockReturnValue(false);

    dispatchDeepLink(window as never, { type: "invite", token: "t" });

    expect(window.restore).toHaveBeenCalled();
    expect(window.show).toHaveBeenCalled();
    expect(window.focus).toHaveBeenCalled();
    expect(window.webContents.send).toHaveBeenCalledWith(IPC_CHANNELS.deepLink, { type: "invite", token: "t" });
  });
});

describe("createDeepLinkRouter", () => {
  let clock = 1_000;
  const now = () => clock;

  beforeEach(() => {
    clock = 1_000;
    vi.clearAllMocks();
  });

  it("창이 로드돼 있으면 이벤트로 보내고, SPA 리스너가 없을 때를 위해 보류에도 둔다", () => {
    const window = fakeWindow();
    const router = createDeepLinkRouter({ getWindow: () => window as never, openWindow: () => window as never, now });

    expect(router.handleUrl("knot://chat/7")).toBe(true);

    expect(window.webContents.send).toHaveBeenCalledWith(IPC_CHANNELS.deepLink, { type: "chat", workspaceId: "7" });
    expect(router.takePending()).toEqual({ type: "chat", workspaceId: "7" });
    expect(router.takePending()).toBeNull();
  });

  it("창이 없으면(콜드 스타트) 창을 만들고 보류만 한다 — 부팅한 SPA가 가져간다", () => {
    const created = fakeWindow(true);
    const openWindow = vi.fn(() => created as never);
    const router = createDeepLinkRouter({ getWindow: () => null, openWindow, now });

    router.handleUrl("knot://invite/tok");

    expect(openWindow).toHaveBeenCalledTimes(1);
    expect(created.webContents.send).not.toHaveBeenCalled();
    expect(router.takePending()).toEqual({ type: "invite", token: "tok" });
  });

  it("창이 아직 로딩 중이면 이벤트를 보내지 않고 보류만 한다", () => {
    const window = fakeWindow(true);
    const router = createDeepLinkRouter({ getWindow: () => window as never, openWindow: () => window as never, now });

    router.handleUrl("knot://invite/tok");

    expect(window.webContents.send).not.toHaveBeenCalled();
    expect(window.focus).toHaveBeenCalled();
    expect(router.takePending()).toEqual({ type: "invite", token: "tok" });
  });

  it("보류 링크는 60초가 지나면 버린다", () => {
    const window = fakeWindow(true);
    const router = createDeepLinkRouter({ getWindow: () => window as never, openWindow: () => window as never, now });

    router.handleUrl("knot://invite/tok");
    clock += 60_001;

    expect(router.takePending()).toBeNull();
  });

  it("나중 링크가 앞선 보류 링크를 대체한다", () => {
    const window = fakeWindow(true);
    const router = createDeepLinkRouter({ getWindow: () => window as never, openWindow: () => window as never, now });

    router.handleUrl("knot://invite/first");
    router.handleUrl("knot://chat/3");

    expect(router.takePending()).toEqual({ type: "chat", workspaceId: "3" });
  });

  it("로그인 콜백은 renderer로 보내지 않고 등록된 수신자에게만 준다", () => {
    const window = fakeWindow();
    const router = createDeepLinkRouter({ getWindow: () => window as never, openWindow: () => window as never, now });
    const handler = vi.fn();
    const off = router.onAuthCallback(handler);

    expect(router.handleUrl("knot://auth/callback?code=dc&state=s")).toBe(true);

    expect(handler).toHaveBeenCalledWith({ type: "auth", code: "dc", state: "s", error: null });
    expect(window.webContents.send).not.toHaveBeenCalled();
    expect(router.takePending()).toBeNull();

    off();
    router.handleUrl("knot://auth/callback?code=x");
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it("문법에 맞지 않는 링크는 거부하고 코드·토큰 값을 로그에 남기지 않는다", () => {
    const window = fakeWindow();
    const router = createDeepLinkRouter({ getWindow: () => window as never, openWindow: () => window as never, now });

    expect(router.handleUrl("knot://evil/secret-token")).toBe(false);

    expect(window.webContents.send).not.toHaveBeenCalled();
    const logged = JSON.stringify([...logMock.info.mock.calls, ...logMock.warn.mock.calls]);
    expect(logged).not.toContain("secret-token");
  });
});

describe("attachDeepLinkHandling", () => {
  it("open-url과 second-instance 인자를 라우터로 넘긴다", () => {
    const router = { handleUrl: vi.fn(), dispatch: vi.fn(), takePending: vi.fn(), onAuthCallback: vi.fn() };
    attachDeepLinkHandling(router);

    const openUrl = appMock.on.mock.calls.find(([name]) => name === "open-url")?.[1] as (e: unknown, u: string) => void;
    const second = appMock.on.mock.calls.find(([name]) => name === "second-instance")?.[1] as (
      e: unknown,
      argv: string[],
    ) => void;
    const event = { preventDefault: vi.fn() };

    openUrl(event, "knot://invite/a");
    second(undefined, ["Knot.exe", "knot://chat/1"]);
    second(undefined, ["Knot.exe"]);

    expect(event.preventDefault).toHaveBeenCalled();
    expect(router.handleUrl).toHaveBeenNthCalledWith(1, "knot://invite/a");
    expect(router.handleUrl).toHaveBeenNthCalledWith(2, "knot://chat/1");
    expect(router.handleUrl).toHaveBeenCalledTimes(2);
  });
});

describe("registerProtocolClient", () => {
  it("knot 스킴을 등록한다", () => {
    expect(registerProtocolClient(["/path/to/Knot"])).toBe(true);
    expect(appMock.setAsDefaultProtocolClient).toHaveBeenCalledWith("knot");
  });

  it("등록에 실패해도 던지지 않고 false를 돌려준다", () => {
    appMock.setAsDefaultProtocolClient.mockReturnValueOnce(false);
    expect(registerProtocolClient(["/path/to/Knot"])).toBe(false);
  });
});
