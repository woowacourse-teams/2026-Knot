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
      "https://api.notion.com",
      "https://www.notion.so",
    ]);
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
