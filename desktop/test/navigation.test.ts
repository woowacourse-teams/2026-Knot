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

const { isAllowedNavigation, isSafeExternalUrl } = await import("../src/main/navigation");

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
