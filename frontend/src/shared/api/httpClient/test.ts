import { AUTH_CSRF_API_PATH } from "@api/fetch/api/v1/auth/csrf";
import { mockServer } from "@api/mock/server";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { getCsrfToken, httpClient } from ".";
import { HTTP_ERROR_TYPE, isHttpError } from "./error";

// 인스턴스 동작만 보는 임시 경로예요
const TEST_PATH = "/__http-client-test";

const CSRF_HEADER_NAME = "X-XSRF-TOKEN";

/** 받은 CSRF 헤더 값을 응답으로 돌려주는 핸들러 */
const echoCsrfHeader = (method: "get" | "post") =>
  http[method](`*${TEST_PATH}`, ({ request }) =>
    HttpResponse.json({ csrf: request.headers.get(CSRF_HEADER_NAME) }),
  );

/** 호출마다 다른 토큰을 내려주는 CSRF 발급 핸들러 */
const rotatingCsrfHandler = () => {
  let issued = 0;

  return http.get(`*${AUTH_CSRF_API_PATH}`, () => {
    issued += 1;
    return HttpResponse.json({ token: `token-${issued}` });
  });
};

// 토큰 캐시가 모듈에 남으므로, 매 테스트가 시작할 때 들고 있는 토큰을 기준으로 기대값을 잡아요
describe("httpClient의 CSRF 처리", () => {
  it("조회 요청에는 CSRF 토큰을 붙이지 않는다", async () => {
    mockServer.use(echoCsrfHeader("get"));

    const { data } = await httpClient.get(TEST_PATH);

    expect(data.csrf).toBeNull();
  });

  it("상태를 바꾸는 요청에는 CSRF 토큰을 헤더로 붙인다", async () => {
    mockServer.use(echoCsrfHeader("post"));

    const { data } = await httpClient.post(TEST_PATH);

    expect(data.csrf).toBe(await getCsrfToken());
  });

  it("403이 오면 토큰을 새로 받아 한 번 다시 보낸다", async () => {
    let attempts = 0;
    mockServer.use(
      rotatingCsrfHandler(),
      http.post(`*${TEST_PATH}`, ({ request }) => {
        attempts += 1;
        if (attempts === 1) return new HttpResponse(null, { status: 403 });

        return HttpResponse.json({ csrf: request.headers.get(CSRF_HEADER_NAME) });
      }),
    );

    const { data } = await httpClient.post(TEST_PATH);

    expect(attempts).toBe(2);
    expect(data.csrf).toBe("token-1");
  });

  it("다시 보낸 요청도 403이면 더 보내지 않고 실패한다", async () => {
    let attempts = 0;
    mockServer.use(
      rotatingCsrfHandler(),
      http.post(`*${TEST_PATH}`, () => {
        attempts += 1;
        return new HttpResponse(null, { status: 403 });
      }),
    );

    const error = await httpClient.post(TEST_PATH).catch((e: unknown) => e);

    expect(isHttpError(error, HTTP_ERROR_TYPE.forbidden)).toBe(true);
    expect(attempts).toBe(2);
  });
});
