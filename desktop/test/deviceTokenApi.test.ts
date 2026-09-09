import { beforeEach, describe, expect, it, vi } from "vitest";
import { logMock } from "./helpers/logMock";

vi.mock("electron-log/main", () => ({ default: logMock }));

const { DEVICE_CODE_INVALID, DeviceAuthError, REFRESH_TOKEN_INVALID, createDeviceTokenApi, isRefreshTokenInvalid } =
  await import("../src/main/auth/deviceTokenApi");

const API_ORIGIN = "https://dev-api.knoted.kr";

const GRANT_BODY = {
  accessToken: "at-1",
  refreshToken: "rt-1",
  expiresIn: 3600,
  session: { id: 7, deviceName: "my-mac" },
};

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  fetchMock.mockReset();
});

function api(timeoutMs?: number) {
  return createDeviceTokenApi({ apiOrigin: API_ORIGIN, fetch: fetchMock, timeoutMs });
}

describe("exchange", () => {
  it("코드·verifier·기기 정보를 본문에 실어 POST하고 Authorization은 붙이지 않는다", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, GRANT_BODY));

    const grant = await api().exchange({
      code: "dc",
      codeVerifier: "ver",
      device: { name: "my-mac", platform: "darwin", appVersion: "0.1.0" },
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe(`${API_ORIGIN}/api/v1/auth/device/token`);
    expect(init?.method).toBe("POST");
    const headers = init?.headers as Record<string, string>;
    expect(Object.keys(headers).map((name) => name.toLowerCase())).not.toContain("authorization");
    expect(JSON.parse(String(init?.body))).toEqual({
      code: "dc",
      codeVerifier: "ver",
      device: { name: "my-mac", platform: "darwin", appVersion: "0.1.0" },
    });
    expect(grant).toEqual({ ...GRANT_BODY, session: { id: "7", deviceName: "my-mac" } });
  });

  it("400 DEVICE_CODE_INVALID는 서버의 코드·문구를 그대로 올린다", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(400, { code: DEVICE_CODE_INVALID, message: "코드가 만료됐어요" }));

    const error = await api()
      .exchange({ code: "old", codeVerifier: "v", device: { name: "n", platform: "p", appVersion: "0" } })
      .catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(DeviceAuthError);
    expect((error as InstanceType<typeof DeviceAuthError>).status).toBe(400);
    expect((error as InstanceType<typeof DeviceAuthError>).code).toBe(DEVICE_CODE_INVALID);
    expect((error as Error).message).toBe("코드가 만료됐어요");
  });

  it("본문을 못 읽은 HTTP 오류는 UNKNOWN이다", async () => {
    fetchMock.mockResolvedValueOnce(new Response("gateway", { status: 502 }));

    const error = (await api()
      .exchange({ code: "c", codeVerifier: "v", device: { name: "n", platform: "p", appVersion: "0" } })
      .catch((caught: unknown) => caught)) as InstanceType<typeof DeviceAuthError>;

    expect(error.status).toBe(502);
    expect(error.code).toBe("UNKNOWN");
  });

  it("네트워크 오류는 KNOT_API_UNREACHABLE이다", async () => {
    fetchMock.mockRejectedValueOnce(new TypeError("fetch failed"));

    const error = (await api()
      .exchange({ code: "c", codeVerifier: "v", device: { name: "n", platform: "p", appVersion: "0" } })
      .catch((caught: unknown) => caught)) as InstanceType<typeof DeviceAuthError>;

    expect(error.code).toBe("KNOT_API_UNREACHABLE");
    expect(error.status).toBeNull();
  });

  it("응답 헤더가 제한 시간 안에 오지 않으면 KNOT_API_UNREACHABLE이다", async () => {
    fetchMock.mockImplementationOnce(
      (_url, init) =>
        new Promise((_resolve, reject) => {
          init?.signal?.addEventListener("abort", () => {
            reject(Object.assign(new Error("timeout"), { name: "TimeoutError" }));
          });
        }),
    );

    const error = (await api(20)
      .exchange({ code: "c", codeVerifier: "v", device: { name: "n", platform: "p", appVersion: "0" } })
      .catch((caught: unknown) => caught)) as InstanceType<typeof DeviceAuthError>;

    expect(error.code).toBe("KNOT_API_UNREACHABLE");
  });

  it("응답 모양이 계약과 다르면 KNOT_API_MALFORMED이다", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { accessToken: "at" }));

    const error = (await api()
      .exchange({ code: "c", codeVerifier: "v", device: { name: "n", platform: "p", appVersion: "0" } })
      .catch((caught: unknown) => caught)) as InstanceType<typeof DeviceAuthError>;

    expect(error.code).toBe("KNOT_API_MALFORMED");
  });
});

describe("refresh", () => {
  it("리프레시 토큰만 본문에 보내고 새 토큰 한 벌을 받는다", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { ...GRANT_BODY, session: { id: "7", deviceName: "my-mac" } }));

    const grant = await api().refresh("rt-old");

    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe(`${API_ORIGIN}/api/v1/auth/device/refresh`);
    expect(JSON.parse(String(init?.body))).toEqual({ refreshToken: "rt-old" });
    expect(grant.refreshToken).toBe("rt-1");
  });

  it("401 REFRESH_TOKEN_INVALID는 로컬 세션을 지워야 하는 오류로 판정된다", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(401, { code: REFRESH_TOKEN_INVALID, message: "무효" }));

    const error = await api().refresh("rt-reused").catch((caught: unknown) => caught);

    expect(isRefreshTokenInvalid(error)).toBe(true);
  });

  it("네트워크 오류는 세션을 지워야 하는 오류가 아니다", async () => {
    fetchMock.mockRejectedValueOnce(new TypeError("fetch failed"));

    const error = await api().refresh("rt").catch((caught: unknown) => caught);

    expect(isRefreshTokenInvalid(error)).toBe(false);
  });
});

describe("revoke", () => {
  it("200이면 본문 없이 끝난다", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 200 }));

    await expect(api().revoke("rt")).resolves.toBeUndefined();
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe(`${API_ORIGIN}/api/v1/auth/device/revoke`);
    expect(JSON.parse(String(init?.body))).toEqual({ refreshToken: "rt" });
  });

  it("HTTP 오류는 DeviceAuthError로 올린다(호출자가 무시할지 정한다)", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 }));

    await expect(api().revoke("rt")).rejects.toBeInstanceOf(DeviceAuthError);
  });
});
