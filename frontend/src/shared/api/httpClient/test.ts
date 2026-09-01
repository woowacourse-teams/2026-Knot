import { beforeEach, describe, expect, it, vi } from "vitest";
import { GetCsrfTokenResponseDto } from "@api/dto/auth";
import { AUTH_CSRF_API_PATH } from "@api/fetch/api/v1/auth/csrf";
import { csrfTokenResponse } from "@api/mock/responses/auth";
import { mockServer } from "@api/mock/server";
import { http, HttpResponse } from "msw";

/** 인터셉터 동작만 보는 임시 경로예요 */
const CSRF_TEST_PATH = "/__csrf-test";

const CSRF_HEADER_NAME = "X-XSRF-TOKEN";

const expectedToken = new GetCsrfTokenResponseDto(csrfTokenResponse).token;

/**
 * 토큰 저장소가 모듈 스코프라 테스트끼리 토큰이 샙니다.
 * 매번 새 모듈로 가져와 저장소를 비운 상태에서 시작해요.
 */
const importHttpClient = async () => {
  vi.resetModules();

  const [{ httpClient }, { getCsrfToken }] = await Promise.all([
    import("./index"),
    import("@/shared/api/httpClient/csrfToken"),
  ]);

  return { httpClient, getCsrfToken };
};

/** 토큰 발급 요청이 몇 번 나갔는지 세는 핸들러를 깔아요 */
const trackCsrfRequest = () => {
  const count = { value: 0 };

  mockServer.use(
    http.get(`*${AUTH_CSRF_API_PATH}`, () => {
      count.value += 1;

      return HttpResponse.json(csrfTokenResponse);
    }),
  );

  return count;
};

/** 변경 요청이 받은 CSRF 헤더를 순서대로 모아요 */
const trackMutatingRequest = (status = 200) => {
  const headers: (string | null)[] = [];

  mockServer.use(
    http.post(`*${CSRF_TEST_PATH}`, ({ request }) => {
      headers.push(request.headers.get(CSRF_HEADER_NAME));

      return HttpResponse.json({}, { status });
    }),
  );

  return headers;
};

describe("httpClient 인증·CSRF 요청 계약", () => {
  beforeEach(() => {
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  it("인증 쿠키를 함께 보내도록 withCredentials가 켜져 있다", async () => {
    const { httpClient } = await importHttpClient();

    expect(httpClient.defaults.withCredentials).toBe(true);
  });

  it("조회 요청에는 CSRF 토큰 헤더를 붙이지 않는다", async () => {
    const { httpClient } = await importHttpClient();
    const headers: (string | null)[] = [];
    mockServer.use(
      http.get(`*${CSRF_TEST_PATH}`, ({ request }) => {
        headers.push(request.headers.get(CSRF_HEADER_NAME));

        return HttpResponse.json({});
      }),
    );

    await httpClient.get(CSRF_TEST_PATH);

    expect(headers).toEqual([null]);
  });

  it.each(["post", "put", "patch", "delete"] as const)(
    "%s 요청에는 CSRF 토큰 헤더를 붙인다",
    async (method) => {
      const { httpClient } = await importHttpClient();
      const headers: (string | null)[] = [];
      mockServer.use(
        http[method](`*${CSRF_TEST_PATH}`, ({ request }) => {
          headers.push(request.headers.get(CSRF_HEADER_NAME));

          return HttpResponse.json({});
        }),
      );

      await httpClient({ method, url: CSRF_TEST_PATH });

      expect(headers).toEqual([expectedToken]);
    },
  );

  it("토큰이 없는 상태의 변경 요청은 토큰을 먼저 받아서 보낸다", async () => {
    const { httpClient } = await importHttpClient();
    const csrfCount = trackCsrfRequest();
    const headers = trackMutatingRequest();

    await httpClient.post(CSRF_TEST_PATH);

    expect(csrfCount.value).toBe(1);
    expect(headers).toEqual([expectedToken]);
  });

  it("한 번 받아둔 토큰은 다음 변경 요청에서 다시 받지 않는다", async () => {
    const { httpClient } = await importHttpClient();
    const csrfCount = trackCsrfRequest();
    trackMutatingRequest();

    await httpClient.post(CSRF_TEST_PATH);
    await httpClient.post(CSRF_TEST_PATH);

    expect(csrfCount.value).toBe(1);
  });

  it("변경 요청이 동시에 나가도 토큰은 한 번만 받는다", async () => {
    const { httpClient } = await importHttpClient();
    const csrfCount = trackCsrfRequest();
    trackMutatingRequest();

    await Promise.all([
      httpClient.post(CSRF_TEST_PATH),
      httpClient.post(CSRF_TEST_PATH),
      httpClient.post(CSRF_TEST_PATH),
    ]);

    expect(csrfCount.value).toBe(1);
  });

  it("403이면 토큰을 새로 받아 새 토큰으로 한 번 다시 보낸다", async () => {
    const { httpClient } = await importHttpClient();
    const reissuedToken = "reissued-csrf-token";
    let isReissued = false;
    mockServer.use(
      http.get(`*${AUTH_CSRF_API_PATH}`, () => {
        const token = isReissued ? reissuedToken : csrfTokenResponse.token;
        isReissued = true;

        return HttpResponse.json({ token });
      }),
    );
    const headers: (string | null)[] = [];
    mockServer.use(
      http.post(`*${CSRF_TEST_PATH}`, ({ request }) => {
        headers.push(request.headers.get(CSRF_HEADER_NAME));

        return request.headers.get(CSRF_HEADER_NAME) === reissuedToken
          ? HttpResponse.json({ ok: true })
          : HttpResponse.json({}, { status: 403 });
      }),
    );

    const response = await httpClient.post(CSRF_TEST_PATH);

    expect(response.data).toEqual({ ok: true });
    expect(headers).toEqual([expectedToken, reissuedToken]);
  });

  it("403이 계속되면 재시도는 한 번으로 끝난다", async () => {
    const { httpClient } = await importHttpClient();
    trackCsrfRequest();
    const headers = trackMutatingRequest(403);

    await expect(httpClient.post(CSRF_TEST_PATH)).rejects.toThrow();

    expect(headers).toHaveLength(2);
  });

  it("토큰 발급 요청 자체가 403이면 다시 시도하지 않는다", async () => {
    const { httpClient } = await importHttpClient();
    let csrfCount = 0;
    mockServer.use(
      http.get(`*${AUTH_CSRF_API_PATH}`, () => {
        csrfCount += 1;

        return HttpResponse.json({}, { status: 403 });
      }),
    );
    const headers = trackMutatingRequest();

    await expect(httpClient.post(CSRF_TEST_PATH)).rejects.toThrow();

    expect(csrfCount).toBe(1);
    expect(headers).toHaveLength(0);
  });

  it("SSE처럼 axios를 쓰지 않는 요청도 같은 토큰을 꺼내 쓸 수 있다", async () => {
    const { getCsrfToken } = await importHttpClient();
    const csrfCount = trackCsrfRequest();

    const token = await getCsrfToken();

    expect(token).toBe(expectedToken);
    expect(csrfCount.value).toBe(1);
  });
});
