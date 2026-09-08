import { describe, expect, it } from "vitest";
import { isKnotEnvName, resolveEnvironment, toOrigin } from "../src/shared/env";

describe("resolveEnvironment", () => {
  it("dev는 저장소에 있는 API 오리진을 그대로 쓴다", () => {
    const env = resolveEnvironment("dev");

    expect(env.webOrigin).toBe("https://dev.knoted.kr");
    expect(env.apiOrigin).toBe("https://dev-api.knoted.kr");
  });

  it("허용 목록에 웹·API·GitHub·Notion 오리진이 모두 들어간다", () => {
    const env = resolveEnvironment("dev");

    expect(env.navigationAllowlist).toEqual([
      "https://dev.knoted.kr",
      "https://dev-api.knoted.kr",
      "https://github.com",
      "https://accounts.google.com",
      "https://appleid.apple.com",
      "https://login.microsoftonline.com",
      "https://login.live.com",
      "https://api.notion.com",
      "https://app.notion.com",
      "https://www.notion.so",
    ]);
  });

  // 2026-09-07 U20 회귀 방지. 이 오리진이 빠지면 Google로 만든 GitHub 계정의
  // 로그인이 외부 브라우저로 새어 나가 세션이 갈리고 콜백 검증이 실패한다.
  it("GitHub 소셜 로그인이 지나가는 Google 오리진이 허용 목록에 있다", () => {
    for (const name of ["dev", "local"] as const) {
      expect(resolveEnvironment(name).navigationAllowlist).toContain("https://accounts.google.com");
    }
  });

  // 2026-09-08 U2 회귀 방지. Notion 동의 화면은 `app.notion.com`에 있고, 이 오리진이
  // 빠지면 `api.notion.com/v1/oauth/authorize`의 302가 `will-redirect`에서 차단돼
  // 외부 브라우저로 빠지고 앱 창의 연결 버튼이 무한 로딩이 된다(기획서 4.5).
  it("Notion 동의 화면 오리진이 허용 목록에 있다", () => {
    for (const name of ["dev", "local"] as const) {
      expect(resolveEnvironment(name).navigationAllowlist).toContain("https://app.notion.com");
    }
  });

  // 2026-09-08 U27 회귀 방지. Notion 로그인 화면의 IdP 팝업은 자식 창으로 열려 같은
  // 허용 목록을 받으므로, `<idp>popupredirect`의 302 목적지가 빠지면 팝업 안에서
  // `will-redirect` 차단 → 외부 브라우저로 빠져 로그인이 끊긴다(기획서 4.5).
  it("Notion 로그인 팝업이 302로 가는 IdP 오리진이 허용 목록에 있다", () => {
    for (const name of ["dev", "local"] as const) {
      const allowlist = resolveEnvironment(name).navigationAllowlist;
      expect(allowlist).toContain("https://login.microsoftonline.com");
      expect(allowlist).toContain("https://appleid.apple.com");
      expect(allowlist).toContain("https://accounts.google.com");
    }
  });

  // 2026-09-07 정정. 웹과 API를 같은 오리진으로 두면 실 백엔드 로그인이
  // `will-navigate`에서 차단되고 `shell.openExternal`도 `http:`라 거부해
  // 로그인 버튼이 무반응이 된다(기획서 4.5).
  it("local은 웹과 실 백엔드 오리진을 모두 허용한다", () => {
    const env = resolveEnvironment("local");

    expect(env.webOrigin).toBe("http://localhost:3000");
    expect(env.apiOrigin).toBe("http://localhost:8080");
    expect(env.navigationAllowlist).toContain("http://localhost:3000");
    expect(env.navigationAllowlist).toContain("http://localhost:8080");
  });

  it("prod는 API 오리진이 주입되지 않으면 빌드를 멈춘다 (Q3)", () => {
    expect(() => resolveEnvironment("prod")).toThrow(/KNOT_API_ORIGIN/);
  });

  it("주입된 API 오리진은 오리진만 남긴다", () => {
    const env = resolveEnvironment("prod", "https://api.knoted.kr/v1/");

    expect(env.apiOrigin).toBe("https://api.knoted.kr");
    expect(env.navigationAllowlist).toContain("https://api.knoted.kr");
  });

  it("API 오리진이 URL이 아니면 빌드를 멈춘다", () => {
    expect(() => resolveEnvironment("prod", "api.knoted.kr")).toThrow(/URL이 아니다/);
  });
});

describe("isKnotEnvName", () => {
  it.each(["prod", "dev", "local"])("%s는 환경 이름이다", (name) => {
    expect(isKnotEnvName(name)).toBe(true);
  });

  it("정의되지 않은 이름은 거부한다", () => {
    expect(isKnotEnvName("staging")).toBe(false);
  });
});

describe("toOrigin", () => {
  it("경로·쿼리를 떼고 오리진만 남긴다", () => {
    expect(toOrigin("https://knoted.kr/invite/abc?code=1")).toBe("https://knoted.kr");
  });

  it("URL이 아니면 null", () => {
    expect(toOrigin("not a url")).toBeNull();
  });
});
