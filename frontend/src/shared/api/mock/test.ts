import { httpClient } from "@api/httpClient";
import { http, HttpResponse } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";

import { mockServer } from "./server";

// 실제 엔드포인트가 아니라 인프라 동작만 보기 위한 임시 경로예요
const SMOKE_PATH = "/__mock-smoke";

describe("msw mock 서버 인프라", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("mockServer.use로 덮은 경로는 httpClient 요청에 그 응답을 돌려준다", async () => {
    mockServer.use(
      http.get(`*${SMOKE_PATH}`, () => HttpResponse.json({ ok: true })),
    );

    const response = await httpClient.get(SMOKE_PATH);

    expect(response.data).toEqual({ ok: true });
  });

  it("핸들러 없는 경로 요청은 onUnhandledRequest: error로 거부된다", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});

    await expect(httpClient.get("/__mock-unhandled")).rejects.toThrow();
  });

  it("afterEach 뒤에는 앞 테스트에서 덮은 핸들러가 남지 않는다", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});

    await expect(httpClient.get(SMOKE_PATH)).rejects.toThrow();
  });
});
