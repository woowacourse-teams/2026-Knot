import { mockServer } from "@api/mock/server";
import axios from "axios";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { httpClient } from ".";

// 인스턴스 동작만 보는 임시 경로예요
const TEST_PATH = "/__http-client-test";

describe("httpClient", () => {
  it("오류를 바꾸지 않고 AxiosError 그대로 던진다", async () => {
    mockServer.use(
      http.post(`*${TEST_PATH}`, () =>
        HttpResponse.json({ code: "ANY" }, { status: 409 }),
      ),
    );

    const error = await httpClient.post(TEST_PATH).catch((caught) => caught);

    expect(axios.isAxiosError(error)).toBe(true);
  });
});
