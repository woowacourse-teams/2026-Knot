import { beforeEach, describe, expect, it, vi } from "vitest";
import { logMock } from "./helpers/logMock";

vi.mock("electron-log/main", () => ({ default: logMock }));

const {
  INVALID_GRANT,
  SUBSCRIPTION_AUTHORIZE_URL,
  SUBSCRIPTION_CLIENT_ID,
  SUBSCRIPTION_TOKEN_URL,
  SubscriptionAuthError,
  buildSubscriptionAuthorizeUrl,
  buildSubscriptionRedirectUri,
  createSubscriptionTokenApi,
  isSubscriptionRefreshInvalid,
} = await import("../src/main/llm/subscriptionOAuth");

const GRANT_BODY = {
  token_type: "Bearer",
  access_token: "sk-ant-oat01-access",
  refresh_token: "sk-ant-ort01-refresh",
  expires_in: 28_800,
  scope: "org:create_api_key user:profile user:inference",
};

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  fetchMock.mockReset();
  logMock.warn.mockReset();
  logMock.info.mockReset();
});

function api(timeoutMs?: number) {
  return createSubscriptionTokenApi({ fetch: fetchMock, timeoutMs });
}

describe("buildSubscriptionAuthorizeUrl", () => {
  it("claude.ai 인가 URL에 Claude Code client_id·PKCE·스코프·loopback redirect를 싣는다(로드맵 Q61 a)", () => {
    const url = new URL(
      buildSubscriptionAuthorizeUrl({ challenge: "chal", state: "st", redirectUri: "http://localhost:4321/callback" }),
    );

    expect(`${url.origin}${url.pathname}`).toBe(SUBSCRIPTION_AUTHORIZE_URL);
    expect(url.searchParams.get("code")).toBe("true");
    expect(url.searchParams.get("client_id")).toBe(SUBSCRIPTION_CLIENT_ID);
    expect(url.searchParams.get("response_type")).toBe("code");
    expect(url.searchParams.get("redirect_uri")).toBe("http://localhost:4321/callback");
    expect(url.searchParams.get("scope")).toBe("org:create_api_key user:profile user:inference");
    expect(url.searchParams.get("code_challenge")).toBe("chal");
    expect(url.searchParams.get("code_challenge_method")).toBe("S256");
    expect(url.searchParams.get("state")).toBe("st");
  });

  it("redirect는 localhost의 loopback 포트 + /callback이다", () => {
    expect(buildSubscriptionRedirectUri(4321)).toBe("http://localhost:4321/callback");
  });
});

describe("exchange", () => {
  it("authorization_code 본문을 JSON으로 POST하고 Authorization은 붙이지 않는다", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, GRANT_BODY));

    const grant = await api().exchange({
      code: "ac",
      state: "st",
      codeVerifier: "ver",
      redirectUri: "http://localhost:4321/callback",
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe(SUBSCRIPTION_TOKEN_URL);
    expect(init?.method).toBe("POST");
    const headers = init?.headers as Record<string, string>;
    expect(headers["Content-Type"]).toBe("application/json");
    expect(Object.keys(headers).map((name) => name.toLowerCase())).not.toContain("authorization");
    expect(JSON.parse(String(init?.body))).toEqual({
      grant_type: "authorization_code",
      code: "ac",
      state: "st",
      redirect_uri: "http://localhost:4321/callback",
      client_id: SUBSCRIPTION_CLIENT_ID,
      code_verifier: "ver",
    });
    expect(grant).toEqual({ accessToken: "sk-ant-oat01-access", refreshToken: "sk-ant-ort01-refresh", expiresIn: 28_800 });
  });

  it("HTTP 오류는 본문의 error·error_description을 그대로 올린다(RFC 6749 §5.2)", async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, { error: "invalid_request", error_description: "code_verifier가 맞지 않아요" }),
    );

    const error = (await api()
      .exchange({ code: "c", state: "s", codeVerifier: "v", redirectUri: "http://localhost:1/callback" })
      .catch((caught: unknown) => caught)) as InstanceType<typeof SubscriptionAuthError>;

    expect(error).toBeInstanceOf(SubscriptionAuthError);
    expect(error.status).toBe(400);
    expect(error.code).toBe("invalid_request");
    expect(error.message).toBe("code_verifier가 맞지 않아요");
  });

  it("본문을 못 읽은 HTTP 오류는 CLAUDE_OAUTH_FAILED다", async () => {
    fetchMock.mockResolvedValueOnce(new Response("gateway", { status: 502 }));

    const error = (await api()
      .exchange({ code: "c", state: "s", codeVerifier: "v", redirectUri: "http://localhost:1/callback" })
      .catch((caught: unknown) => caught)) as InstanceType<typeof SubscriptionAuthError>;

    expect(error.status).toBe(502);
    expect(error.code).toBe("CLAUDE_OAUTH_FAILED");
  });

  it("네트워크 오류는 CLAUDE_OAUTH_UNREACHABLE이다", async () => {
    fetchMock.mockRejectedValueOnce(new TypeError("fetch failed"));

    const error = (await api()
      .exchange({ code: "c", state: "s", codeVerifier: "v", redirectUri: "http://localhost:1/callback" })
      .catch((caught: unknown) => caught)) as InstanceType<typeof SubscriptionAuthError>;

    expect(error.status).toBeNull();
    expect(error.code).toBe("CLAUDE_OAUTH_UNREACHABLE");
  });

  it("타임아웃도 CLAUDE_OAUTH_UNREACHABLE이다", async () => {
    fetchMock.mockImplementationOnce(
      (_url, init) =>
        new Promise((_resolve, reject) => {
          init?.signal?.addEventListener("abort", () => reject(init.signal?.reason as Error));
        }),
    );

    const error = (await api(20)
      .exchange({ code: "c", state: "s", codeVerifier: "v", redirectUri: "http://localhost:1/callback" })
      .catch((caught: unknown) => caught)) as InstanceType<typeof SubscriptionAuthError>;

    expect(error.code).toBe("CLAUDE_OAUTH_UNREACHABLE");
  });

  it.each([
    ["access_token 없음", { refresh_token: "r", expires_in: 10 }],
    ["expires_in 0", { access_token: "a", refresh_token: "r", expires_in: 0 }],
    ["expires_in 문자열", { access_token: "a", refresh_token: "r", expires_in: "10" }],
    ["refresh_token 숫자", { access_token: "a", refresh_token: 1, expires_in: 10 }],
    ["객체 아님", "nope"],
  ])("모양이 계약과 다르면 CLAUDE_OAUTH_MALFORMED다: %s", async (_label, body) => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, body));

    const error = (await api()
      .exchange({ code: "c", state: "s", codeVerifier: "v", redirectUri: "http://localhost:1/callback" })
      .catch((caught: unknown) => caught)) as InstanceType<typeof SubscriptionAuthError>;

    expect(error.code).toBe("CLAUDE_OAUTH_MALFORMED");
  });

  it("코드·verifier·토큰 값은 로그에 남지 않는다", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(400, { error: "invalid_grant" }));
    await api()
      .exchange({ code: "SECRET-CODE", state: "SECRET-STATE", codeVerifier: "SECRET-VER", redirectUri: "http://localhost:1/callback" })
      .catch(() => {});
    fetchMock.mockResolvedValueOnce(jsonResponse(200, GRANT_BODY));
    await api().exchange({ code: "SECRET-CODE", state: "s", codeVerifier: "SECRET-VER", redirectUri: "http://localhost:1/callback" });

    const logged = JSON.stringify([...logMock.info.mock.calls, ...logMock.warn.mock.calls]);
    expect(logged).not.toContain("SECRET");
    expect(logged).not.toContain("sk-ant-");
  });
});

describe("refresh", () => {
  it("refresh_token 본문으로 같은 엔드포인트를 부른다", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, GRANT_BODY));

    const grant = await api().refresh("sk-ant-ort01-old");

    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe(SUBSCRIPTION_TOKEN_URL);
    expect(JSON.parse(String(init?.body))).toEqual({
      grant_type: "refresh_token",
      refresh_token: "sk-ant-ort01-old",
      client_id: SUBSCRIPTION_CLIENT_ID,
    });
    expect(grant.refreshToken).toBe("sk-ant-ort01-refresh");
  });

  it("갱신 응답에 refresh_token이 없으면 null로 돌려준다(호출자가 기존 값을 유지)", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { access_token: "a2", expires_in: 100 }));

    const grant = await api().refresh("old");

    expect(grant).toEqual({ accessToken: "a2", refreshToken: null, expiresIn: 100 });
  });
});

describe("isSubscriptionRefreshInvalid", () => {
  it("invalid_grant 또는 401만 무효로 본다", () => {
    expect(isSubscriptionRefreshInvalid(new SubscriptionAuthError(400, INVALID_GRANT, "m"))).toBe(true);
    expect(isSubscriptionRefreshInvalid(new SubscriptionAuthError(401, "unauthorized", "m"))).toBe(true);
    expect(isSubscriptionRefreshInvalid(new SubscriptionAuthError(null, "CLAUDE_OAUTH_UNREACHABLE", "m"))).toBe(false);
    expect(isSubscriptionRefreshInvalid(new SubscriptionAuthError(500, "server_error", "m"))).toBe(false);
    expect(isSubscriptionRefreshInvalid(new Error("x"))).toBe(false);
  });
});
