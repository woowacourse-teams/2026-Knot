import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { csrfTokenResponse } from "@api/mock/responses/auth";
import { mockServer } from "@api/mock/server";

import { requestSseStream, SseRequestError } from ".";

const SSE_PATH = "/api/v1/sse-test";

/** 받은 요청을 꺼내 볼 수 있게 남기고 이벤트 하나로 응답해요 */
const captureRequest = () => {
  const requests: Request[] = [];

  mockServer.use(
    http.all(`*${SSE_PATH}`, ({ request }) => {
      requests.push(request.clone());

      return new HttpResponse("data: 1\n\n", {
        headers: { "Content-Type": "text/event-stream" },
      });
    }),
  );

  return requests;
};

/** 스트림을 끝까지 받다가 throw된 오류를 돌려줘요 */
const catchError = async (stream: AsyncGenerator) => {
  try {
    for await (const _ of stream);
  } catch (error) {
    return error;
  }
};

describe("requestSseStream", () => {
  it("POST는 본문을 JSON으로 실어 SSE와 CSRF 헤더와 함께 보낸다", async () => {
    const requests = captureRequest();
    const events = [];

    for await (const event of requestSseStream({
      method: "POST",
      path: SSE_PATH,
      body: { content: "질문" },
    })) {
      events.push(event);
    }

    const [request] = requests;
    expect(new URL(request.url).pathname).toBe(SSE_PATH);
    expect(request.method).toBe("POST");
    expect(request.headers.get("Accept")).toBe("text/event-stream");
    expect(request.headers.get("Content-Type")).toBe("application/json");
    expect(request.headers.get("X-XSRF-TOKEN")).toBe(csrfTokenResponse.token);
    expect(await request.json()).toEqual({ content: "질문" });
    expect(events).toEqual([{ event: "message", data: "1" }]);
  });

  it("본문 없는 GET은 본문과 Content-Type 없이 보낸다", async () => {
    const requests = captureRequest();

    for await (const _ of requestSseStream({ method: "GET", path: SSE_PATH }));

    const [request] = requests;
    expect(request.method).toBe("GET");
    expect(request.headers.get("Content-Type")).toBeNull();
    expect(await request.text()).toBe("");
  });

  it("GET은 httpClient처럼 CSRF 헤더를 붙이지 않는다", async () => {
    const requests = captureRequest();

    for await (const _ of requestSseStream({ method: "GET", path: SSE_PATH }));

    const [request] = requests;
    expect(request.headers.get("X-XSRF-TOKEN")).toBeNull();
  });

  it("실패 응답 본문을 읽지 못하면 code를 UNKNOWN으로 담아 throw한다", async () => {
    mockServer.use(
      http.get(`*${SSE_PATH}`, () => new HttpResponse("오류", { status: 500 })),
    );

    const error = await catchError(
      requestSseStream({ method: "GET", path: SSE_PATH }),
    );

    expect(error).toBeInstanceOf(SseRequestError);
    expect(error).toMatchObject({ status: 500, code: "UNKNOWN" });
  });

  it("성공 응답에 본문이 없으면 EMPTY_BODY 오류로 throw한다", async () => {
    mockServer.use(
      http.get(`*${SSE_PATH}`, () => new HttpResponse(null, { status: 200 })),
    );

    const error = await catchError(
      requestSseStream({ method: "GET", path: SSE_PATH }),
    );

    expect(error).toBeInstanceOf(SseRequestError);
    expect(error).toMatchObject({ status: 200, code: "EMPTY_BODY" });
  });

  it("abort로 요청이 끊기면 SseRequestError로 바꾸지 않고 그대로 던진다", async () => {
    captureRequest();
    const controller = new AbortController();
    controller.abort();

    const error = await catchError(
      requestSseStream({
        method: "GET",
        path: SSE_PATH,
        signal: controller.signal,
      }),
    );

    expect(error).not.toBeInstanceOf(SseRequestError);
    expect(error).toMatchObject({ name: "AbortError" });
  });
});
