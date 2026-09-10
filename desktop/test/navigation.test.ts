import { describe, expect, it, vi } from "vitest";

// `vi.mock`은 호이스팅되므로 목 객체도 `vi.hoisted`로 먼저 만든다
const shellMock = vi.hoisted(() => ({ openExternal: vi.fn(() => Promise.resolve()) }));

vi.mock("electron", () => ({ shell: shellMock }));

vi.mock("electron-log/main", () => ({
  default: {
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    transports: {
      file: { getFile: () => ({ path: "" }) },
      console: {},
    },
  },
}));

const {
  allowExtraOrigins,
  allowlistFor,
  applyLoginViewWindowPolicy,
  isAllowedNavigation,
  isSafeExternalUrl,
  resolveWindowOpen,
  revokeExtraOrigins,
} = await import("../src/main/navigation");
const envModule = await import("../src/shared/env");

const ALLOWLIST = [
  "https://dev.knoted.kr",
  "https://dev-api.knoted.kr",
  "https://github.com",
] as const;

describe("isAllowedNavigation", () => {
  it("허용 목록의 오리진은 경로가 달라도 통과한다", () => {
    expect(isAllowedNavigation("https://dev.knoted.kr/workspace/1/chat", ALLOWLIST)).toBe(true);
    expect(isAllowedNavigation("https://github.com/login/oauth/authorize?x=1", ALLOWLIST)).toBe(true);
  });

  it("목록에 없는 하위 도메인은 막는다", () => {
    expect(isAllowedNavigation("https://evil.dev.knoted.kr/", ALLOWLIST)).toBe(false);
  });

  it("오리진이 같아 보여도 스킴이 다르면 막는다", () => {
    expect(isAllowedNavigation("http://dev.knoted.kr/", ALLOWLIST)).toBe(false);
  });

  it("URL이 아니거나 오리진이 없는 스킴은 막는다", () => {
    expect(isAllowedNavigation("about:blank", ALLOWLIST)).toBe(false);
    expect(isAllowedNavigation("javascript:alert(1)", ALLOWLIST)).toBe(false);
    expect(isAllowedNavigation("", ALLOWLIST)).toBe(false);
  });
});

// 2026-09-08 Q46. Notion 로그인 화면의 IdP 팝업은 `window.opener`가 없으면 스스로 닫히므로
// 허용 목록 안 오리진의 새 창은 자식 창으로 열어야 한다. 전부 거부하면 "팝업이 차단됨"이 된다.
describe("resolveWindowOpen", () => {
  const NOTION_POPUP =
    "https://app.notion.com/verifyNoPopupBlockerHtmlAndRedirect?redirectUri=https%3A%2F%2Fapp.notion.com%2Fmicrosoftpopupredirect";
  const ALLOWLIST_WITH_NOTION = [...ALLOWLIST, "https://app.notion.com"] as const;

  it("허용 목록 안 오리진의 새 창은 자식 창으로 연다", () => {
    const response = resolveWindowOpen(NOTION_POPUP, ALLOWLIST_WITH_NOTION);
    expect(response.action).toBe("allow");
  });

  it("자식 창에도 보안 webPreferences를 다시 명시하고 preload는 주지 않는다", () => {
    const response = resolveWindowOpen(NOTION_POPUP, ALLOWLIST_WITH_NOTION);
    expect(response.overrideBrowserWindowOptions?.webPreferences).toEqual({
      sandbox: true,
      contextIsolation: true,
      nodeIntegration: false,
      webviewTag: false,
    });
    expect(response.overrideBrowserWindowOptions?.webPreferences).not.toHaveProperty("preload");
  });

  it("허용 목록 밖 오리진의 새 창은 거부한다", () => {
    expect(resolveWindowOpen(NOTION_POPUP, ALLOWLIST)).toEqual({ action: "deny" });
    expect(resolveWindowOpen("https://evil.example/login", ALLOWLIST_WITH_NOTION)).toEqual({
      action: "deny",
    });
  });

  it("오리진이 없는 스킴은 거부한다", () => {
    expect(resolveWindowOpen("about:blank", ALLOWLIST_WITH_NOTION)).toEqual({ action: "deny" });
    expect(resolveWindowOpen("javascript:alert(1)", ALLOWLIST_WITH_NOTION)).toEqual({
      action: "deny",
    });
  });
});

describe("isSafeExternalUrl", () => {
  it("https와 mailto만 외부로 넘긴다", () => {
    expect(isSafeExternalUrl("https://www.notion.so/page")).toBe(true);
    expect(isSafeExternalUrl("mailto:team@knoted.kr")).toBe(true);
  });

  it("그 밖의 스킴은 거부한다", () => {
    expect(isSafeExternalUrl("http://knoted.kr")).toBe(false);
    expect(isSafeExternalUrl("file:///etc/passwd")).toBe(false);
    expect(isSafeExternalUrl("javascript:alert(1)")).toBe(false);
    expect(isSafeExternalUrl("knot://invite/abc")).toBe(false);
    expect(isSafeExternalUrl("nope")).toBe(false);
  });
});

// 2026-09-09 Q68. 2단계 로그인 뷰는 백엔드가 302로 보내는 `http://127.0.0.1:{port}/callback`을
// 통과해야 하지만, 그 오리진을 빌드 상수 목록에 넣으면 모든 창이 거기로 갈 수 있다.
// 그래서 로그인 뷰(webContents)에만 한시로 붙이고 뷰가 사라지면 함께 지운다.
describe("allowExtraOrigins / allowlistFor", () => {
  type WebContents = import("electron").WebContents;
  type KnotEnvironment = import("../src/shared/env").KnotEnvironment;
  const { resolveEnvironment } = envModule;
  const env = { navigationAllowlist: ALLOWLIST } as unknown as KnotEnvironment;
  const LOOPBACK = "http://127.0.0.1:43111";

  function fakeContents(id: number) {
    const handlers = new Map<string, () => void>();
    const contents = {
      id,
      once: (name: string, handler: () => void) => {
        handlers.set(name, handler);
        return contents;
      },
    };
    return { contents: contents as unknown as WebContents, destroy: () => handlers.get("destroyed")?.() };
  }

  it("등록하지 않은 창은 빌드 상수 목록만 쓴다 — loopback 오리진은 어느 환경 목록에도 없다", () => {
    const { contents } = fakeContents(1);
    expect(allowlistFor(contents, env)).toBe(env.navigationAllowlist);
    for (const name of ["prod", "dev", "local"] as const) {
      const list = resolveEnvironment(name, "https://api.example").navigationAllowlist;
      expect(list.some((origin) => origin.startsWith("http://127.0.0.1"))).toBe(false);
    }
    expect(isAllowedNavigation(`${LOOPBACK}/callback?code=x`, allowlistFor(contents, env))).toBe(false);
  });

  it("등록한 창에만 loopback 오리진이 더해지고 다른 창에는 새지 않는다", () => {
    const login = fakeContents(2);
    const main = fakeContents(3);
    allowExtraOrigins(login.contents, [LOOPBACK]);

    expect(isAllowedNavigation(`${LOOPBACK}/callback?code=x`, allowlistFor(login.contents, env))).toBe(true);
    expect(isAllowedNavigation("https://github.com/login", allowlistFor(login.contents, env))).toBe(true);
    expect(isAllowedNavigation(`${LOOPBACK}/callback?code=x`, allowlistFor(main.contents, env))).toBe(false);
    // 같은 호스트라도 포트가 다르면 다른 오리진이다
    expect(isAllowedNavigation("http://127.0.0.1:43112/callback", allowlistFor(login.contents, env))).toBe(false);
    expect(isAllowedNavigation("http://localhost:43111/callback", allowlistFor(login.contents, env))).toBe(false);
  });

  it("창이 파괴되면 허용이 사라진다", () => {
    const login = fakeContents(4);
    allowExtraOrigins(login.contents, [LOOPBACK]);
    login.destroy();
    expect(allowlistFor(login.contents, env)).toBe(env.navigationAllowlist);
  });

  it("셸이 먼저 회수하면 창이 살아 있어도 허용이 사라진다", () => {
    const login = fakeContents(5);
    allowExtraOrigins(login.contents, [LOOPBACK]);
    revokeExtraOrigins(login.contents);
    expect(isAllowedNavigation(`${LOOPBACK}/callback`, allowlistFor(login.contents, env))).toBe(false);
  });
});

// 2026-09-10 Q69. 사용자가 로그인 중 새 창이 뜨는 것을 금지했다. 로그인 뷰에서는 창을 하나도
// 만들지 않고, 허용 목록 안 URL이면 같은 뷰에서 이동시킨다(Q46의 자식 창 허용은 메인 창 전용).
describe("applyLoginViewWindowPolicy", () => {
  type WebContents = import("electron").WebContents;
  type KnotEnvironment = import("../src/shared/env").KnotEnvironment;
  const env = { navigationAllowlist: ALLOWLIST } as unknown as KnotEnvironment;

  function fakeLoginView(id: number) {
    let handler: (details: { url: string }) => { action: string } = () => ({ action: "deny" });
    const loadURL = vi.fn(() => Promise.resolve());
    const contents = {
      id,
      loadURL,
      once: () => contents,
      setWindowOpenHandler: (next: typeof handler) => {
        handler = next;
      },
    };
    return {
      contents: contents as unknown as WebContents,
      loadURL,
      open: (url: string) => handler({ url }),
    };
  }

  it("허용 목록 안 URL은 창을 만들지 않고 같은 뷰에서 연다", () => {
    const view = fakeLoginView(11);
    applyLoginViewWindowPolicy(view.contents, env);

    expect(view.open("https://github.com/login/oauth/authorize?x=1")).toEqual({ action: "deny" });
    expect(view.loadURL).toHaveBeenCalledWith("https://github.com/login/oauth/authorize?x=1");
  });

  it("목록 밖 URL은 거부하고 이동도 하지 않는다 — 외부 브라우저로도 넘기지 않는다", () => {
    const view = fakeLoginView(12);
    applyLoginViewWindowPolicy(view.contents, env);

    expect(view.open("https://evil.example/login")).toEqual({ action: "deny" });
    expect(view.loadURL).not.toHaveBeenCalled();
    expect(shellMock.openExternal).not.toHaveBeenCalled();
  });

  it("그 뷰에 한시 허용된 loopback 콜백도 같은 뷰에서 연다", () => {
    const view = fakeLoginView(13);
    allowExtraOrigins(view.contents, ["http://127.0.0.1:43111"]);
    applyLoginViewWindowPolicy(view.contents, env);

    expect(view.open("http://127.0.0.1:43111/callback?code=x")).toEqual({ action: "deny" });
    expect(view.loadURL).toHaveBeenCalledWith("http://127.0.0.1:43111/callback?code=x");
  });
});
