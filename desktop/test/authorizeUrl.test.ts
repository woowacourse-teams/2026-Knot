import { describe, expect, it } from "vitest";
import { buildAuthorizeUrl, isCallbackLike, parseCallbackParams } from "../src/main/auth/authorizeUrl";

describe("buildAuthorizeUrl", () => {
  it("기획서 5.2 계약대로 desktop 클라이언트·S256·loopback 반환 포트를 싣는다", () => {
    const url = new URL(
      buildAuthorizeUrl({
        apiOrigin: "https://dev-api.knoted.kr",
        challenge: "chal",
        state: "st",
        loopbackPort: 51234,
      }),
    );

    expect(url.origin).toBe("https://dev-api.knoted.kr");
    expect(url.pathname).toBe("/oauth2/authorization/github");
    expect(url.searchParams.get("client")).toBe("desktop");
    expect(url.searchParams.get("code_challenge")).toBe("chal");
    expect(url.searchParams.get("code_challenge_method")).toBe("S256");
    expect(url.searchParams.get("state")).toBe("st");
    expect(url.searchParams.get("return")).toBe("loopback:51234");
  });

  it("local 빌드의 http API 오리진도 그대로 쓴다", () => {
    expect(
      buildAuthorizeUrl({ apiOrigin: "http://localhost:8080", challenge: "c", state: "s", loopbackPort: 1 }),
    ).toMatch(/^http:\/\/localhost:8080\/oauth2\/authorization\/github\?/);
  });
});

describe("parseCallbackParams", () => {
  it("code·state를 읽는다", () => {
    const params = parseCallbackParams(new URLSearchParams("code=dc&state=st"));
    expect(params).toEqual({ code: "dc", state: "st" });
    expect(isCallbackLike(params)).toBe(true);
  });

  it("실패 콜백은 error만 있다", () => {
    const params = parseCallbackParams(new URLSearchParams("error=access_denied"));
    expect(params).toEqual({ error: "access_denied" });
    expect(isCallbackLike(params)).toBe(true);
  });

  it("빈 값은 없는 것으로 보고, 코드도 error도 없으면 콜백이 아니다", () => {
    const params = parseCallbackParams(new URLSearchParams("code=&state=st&error="));
    expect(params).toEqual({ state: "st" });
    expect(isCallbackLike(params)).toBe(false);
  });
});
