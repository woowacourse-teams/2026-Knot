import { describe, expect, it, vi } from "vitest";

vi.mock("electron", () => ({
  shell: { openExternal: vi.fn() },
}));

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

const { isAllowedNavigation, isSafeExternalUrl, resolveWindowOpen } = await import(
  "../src/main/navigation"
);

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
