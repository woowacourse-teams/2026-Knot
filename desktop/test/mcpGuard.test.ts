import { describe, expect, it } from "vitest";
import { checkMcpRequest } from "../src/mcp/guard";

const TOKEN = "ZmFrZS10b2tlbi1mb3ItdGVzdHMtMDEyMzQ1Njc4OQ";
const expected = { port: 47871, token: TOKEN };

function request(overrides: { url?: string; headers?: Record<string, string> } = {}) {
  return {
    method: "POST",
    url: overrides.url ?? "/mcp",
    headers: {
      host: "127.0.0.1:47871",
      authorization: `Bearer ${TOKEN}`,
      ...overrides.headers,
    },
  };
}

describe("checkMcpRequest (로드맵 Q48)", () => {
  it("127.0.0.1 Host + 올바른 Bearer 토큰이면 통과한다", () => {
    expect(checkMcpRequest(request(), expected)).toEqual({ ok: true });
  });

  it("localhost Host도 대소문자 구분 없이 통과한다", () => {
    expect(checkMcpRequest(request({ headers: { host: "LocalHost:47871" } }), expected)).toEqual({ ok: true });
  });

  it("쿼리스트링은 무시하고 경로만 본다", () => {
    expect(checkMcpRequest(request({ url: "/mcp?x=1" }), expected)).toEqual({ ok: true });
  });

  it("/mcp가 아닌 경로는 404", () => {
    expect(checkMcpRequest(request({ url: "/" }), expected)).toMatchObject({ ok: false, status: 404 });
    expect(checkMcpRequest(request({ url: "/mcp/extra" }), expected)).toMatchObject({ ok: false, status: 404 });
  });

  it("Origin 헤더가 있으면 값과 무관하게 403 (브라우저 출처·DNS 리바인딩 차단)", () => {
    expect(checkMcpRequest(request({ headers: { origin: "http://127.0.0.1:47871" } }), expected)).toMatchObject({
      ok: false,
      status: 403,
    });
    expect(checkMcpRequest(request({ headers: { origin: "null" } }), expected)).toMatchObject({ ok: false, status: 403 });
  });

  it("Host가 127.0.0.1·localhost + 포트가 아니면 403", () => {
    for (const host of ["127.0.0.1:1234", "localhost", "knot.local:47871", "0.0.0.0:47871", "192.168.0.2:47871"]) {
      expect(checkMcpRequest(request({ headers: { host } }), expected), host).toMatchObject({ ok: false, status: 403 });
    }
  });

  it("Host가 없으면 403", () => {
    const { host: _host, ...headers } = request().headers;
    expect(checkMcpRequest({ url: "/mcp", headers }, expected)).toMatchObject({ ok: false, status: 403 });
  });

  it("Authorization이 없거나 Bearer가 아니거나 토큰이 다르면 401", () => {
    const { authorization: _auth, ...headers } = request().headers;
    expect(checkMcpRequest({ url: "/mcp", headers }, expected)).toMatchObject({ ok: false, status: 401 });
    expect(checkMcpRequest(request({ headers: { authorization: `Basic ${TOKEN}` } }), expected)).toMatchObject({
      status: 401,
    });
    expect(checkMcpRequest(request({ headers: { authorization: `Bearer ${TOKEN}x` } }), expected)).toMatchObject({
      status: 401,
    });
    expect(checkMcpRequest(request({ headers: { authorization: `Bearer ${TOKEN.slice(1)}` } }), expected)).toMatchObject(
      { status: 401 },
    );
    expect(checkMcpRequest(request({ headers: { authorization: "Bearer " } }), expected)).toMatchObject({ status: 401 });
  });

  it("Origin·Host 검사가 토큰 검사보다 먼저다 (브라우저가 토큰 유무를 알 수 없다)", () => {
    const { authorization: _auth, ...headers } = request().headers;
    expect(checkMcpRequest({ url: "/mcp", headers: { ...headers, origin: "http://evil.test" } }, expected)).toMatchObject({
      status: 403,
    });
  });
});
