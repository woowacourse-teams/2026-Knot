import { describe, expect, it } from "vitest";
import { withBearerHeader } from "../src/main/auth/bearerInjector";

const API_ORIGIN = "https://dev-api.knoted.kr";

function xhr(url: string, requestHeaders: Record<string, string> = {}) {
  return { url, resourceType: "xhr", requestHeaders };
}

describe("withBearerHeader", () => {
  it("API 오리진의 XHR에 토큰이 있고 헤더가 없으면 Authorization을 붙인다", () => {
    const headers = withBearerHeader(xhr(`${API_ORIGIN}/api/v1/workspaces`, { Accept: "application/json" }), API_ORIGIN, "tok");
    expect(headers).toEqual({ Accept: "application/json", Authorization: "Bearer tok" });
  });

  it("토큰이 없으면 요청을 건드리지 않는다", () => {
    expect(withBearerHeader(xhr(`${API_ORIGIN}/api/v1/auth/me`), API_ORIGIN, null)).toBeNull();
    expect(withBearerHeader(xhr(`${API_ORIGIN}/api/v1/auth/me`), API_ORIGIN, "")).toBeNull();
  });

  it("renderer가 이미 붙인 Authorization은 대소문자와 무관하게 존중한다", () => {
    expect(withBearerHeader(xhr(`${API_ORIGIN}/x`, { Authorization: "Bearer mine" }), API_ORIGIN, "tok")).toBeNull();
    expect(withBearerHeader(xhr(`${API_ORIGIN}/x`, { authorization: "Bearer mine" }), API_ORIGIN, "tok")).toBeNull();
  });

  it("다른 오리진에는 절대 붙이지 않는다(불변 계약 10번)", () => {
    expect(withBearerHeader(xhr("https://evil.dev-api.knoted.kr/x"), API_ORIGIN, "tok")).toBeNull();
    expect(withBearerHeader(xhr("https://github.com/login"), API_ORIGIN, "tok")).toBeNull();
    expect(withBearerHeader(xhr("http://dev-api.knoted.kr/x"), API_ORIGIN, "tok")).toBeNull();
    expect(withBearerHeader(xhr("not a url"), API_ORIGIN, "tok")).toBeNull();
  });

  it("페이지 이동(mainFrame·subFrame)에는 붙이지 않는다", () => {
    for (const resourceType of ["mainFrame", "subFrame"]) {
      expect(
        withBearerHeader({ url: `${API_ORIGIN}/oauth2/authorization/github`, resourceType, requestHeaders: {} }, API_ORIGIN, "tok"),
      ).toBeNull();
    }
  });
});
