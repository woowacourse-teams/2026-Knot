import { mockServer } from "@api/mock/server";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { apiClient, isApiError } from ".";

// 인스턴스 동작만 보는 임시 경로예요
const TEST_PATH = "/__api-client-test";

describe("apiClient", () => {
  it("서버가 오류 본문을 주면 상태와 code를 담은 ApiError로 바꿔 던진다", async () => {
    mockServer.use(
      http.post(`*${TEST_PATH}`, () =>
        HttpResponse.json({ code: "RECORDING_ALREADY_ENDED" }, { status: 409 }),
      ),
    );

    const error = await apiClient.post(TEST_PATH).catch((caught) => caught);

    expect(isApiError(error)).toBe(true);
    expect(error).toMatchObject({
      status: 409,
      code: "RECORDING_ALREADY_ENDED",
    });
  });

  it("응답을 받지 못한 네트워크 오류는 상태와 code가 없는 ApiError로 던진다", async () => {
    mockServer.use(http.get(`*${TEST_PATH}`, () => HttpResponse.error()));

    const error = await apiClient.get(TEST_PATH).catch((caught) => caught);

    expect(isApiError(error)).toBe(true);
    expect(error).toMatchObject({ status: undefined, code: undefined });
  });

  it("CSRF 토큰이 낡아 403이 오면 토큰을 다시 받아 한 번 더 보낸다", async () => {
    let requestCount = 0;
    mockServer.use(
      http.post(`*${TEST_PATH}`, () => {
        requestCount += 1;

        return requestCount === 1
          ? HttpResponse.json(null, { status: 403 })
          : HttpResponse.json({ ok: true });
      }),
    );

    const response = await apiClient.post(TEST_PATH);

    expect(response.data).toEqual({ ok: true });
    expect(requestCount).toBe(2);
  });
});
