import { createServer } from "node:http";
import type { Server } from "node:http";
import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { collect, sseResponse } from "./helpers/sseResponse";

vi.mock("electron-log/main", () => ({
  default: {
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    transports: { file: { getFile: () => ({ path: "" }) }, console: {} },
  },
}));

const { streamOpenAiCompatible } = await import("../src/main/llm/openAiCompatibleClient");
const { LlmError } = await import("../src/main/llm/errors");

const REQUEST = { system: "규칙", messages: [{ role: "user" as const, content: "질문" }] };

function options(fetchImpl: typeof fetch, apiKey: string | null = null, signal = new AbortController().signal) {
  return { baseUrl: "http://localhost:1234/v1", model: "qwen/qwen3.6-27b", apiKey, signal, fetch: fetchImpl };
}

const chunk = (content: string, finish: string | null = null) =>
  `data: ${JSON.stringify({ choices: [{ delta: { content }, finish_reason: finish }] })}\n\n`;

describe("streamOpenAiCompatible", () => {
  it("system을 첫 메시지로 넣고 model·messages·stream만 보낸다 (Q43)", async () => {
    const fetchMock = vi.fn(async () => sseResponse([chunk("안"), chunk("녕"), "data: [DONE]\n\n"]));

    const deltas = await collect(streamOpenAiCompatible(REQUEST, options(fetchMock)));

    expect(deltas).toEqual(["안", "녕"]);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("http://localhost:1234/v1/chat/completions");
    expect(JSON.parse(init.body as string)).toEqual({
      model: "qwen/qwen3.6-27b",
      messages: [
        { role: "system", content: "규칙" },
        { role: "user", content: "질문" },
      ],
      stream: true,
    });
    expect((init.headers as Record<string, string>)["Authorization"]).toBeUndefined();
  });

  it("키가 있으면 Bearer 헤더를 붙인다", async () => {
    const fetchMock = vi.fn(async () => sseResponse(["data: [DONE]\n\n"]));

    await collect(streamOpenAiCompatible(REQUEST, options(fetchMock, "sk-x")));

    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect((init.headers as Record<string, string>)["Authorization"]).toBe("Bearer sk-x");
  });

  it("content 없는 조각은 건너뛰고 finish_reason만 와도 정상 종료로 본다", async () => {
    const fetchMock = vi.fn(async () =>
      sseResponse([`data: ${JSON.stringify({ choices: [{ delta: { role: "assistant" } }] })}\n\n`, chunk("답", "stop")]),
    );

    expect(await collect(streamOpenAiCompatible(REQUEST, options(fetchMock)))).toEqual(["답"]);
  });

  it("종료 표시 없이 끊기면 LLM_STREAM_FAILED", async () => {
    const fetchMock = vi.fn(async () => sseResponse([chunk("답")]));

    await expect(collect(streamOpenAiCompatible(REQUEST, options(fetchMock)))).rejects.toMatchObject({
      code: "LLM_STREAM_FAILED",
    });
  });

  it("HTTP 상태를 코드로 매핑한다", async () => {
    for (const [status, code] of [
      [401, "LLM_CONFIGURATION_INVALID"],
      [403, "LLM_CONFIGURATION_INVALID"],
      [429, "LLM_RATE_LIMITED"],
      [500, "LLM_STREAM_FAILED"],
    ] as const) {
      const fetchMock = vi.fn(async () => new Response("{}", { status }));
      await expect(collect(streamOpenAiCompatible(REQUEST, options(fetchMock)))).rejects.toMatchObject({ code });
    }
  });

  it("연결 실패는 LLM_STREAM_FAILED, 취소는 AbortError 그대로", async () => {
    const failing = vi.fn(async () => {
      throw new TypeError("fetch failed");
    });
    await expect(collect(streamOpenAiCompatible(REQUEST, options(failing)))).rejects.toBeInstanceOf(LlmError);

    const aborting = vi.fn(async () => {
      throw new DOMException("aborted", "AbortError");
    });
    await expect(collect(streamOpenAiCompatible(REQUEST, options(aborting)))).rejects.toMatchObject({
      name: "AbortError",
    });
  });
});

// 로드맵 U24: Node fetch(undici)가 SSE를 끊김 없이 읽고 AbortController로 취소되는가.
// 실제 HTTP 서버를 띄워 전역 fetch로 측정한다(vitest의 Node 22. Electron 44는 Node 24를 번들한다).
describe("실제 HTTP 서버 스트리밍 (U24)", () => {
  let server: Server;
  let baseUrl = "";
  let closedByClient = false;

  beforeAll(async () => {
    server = createServer((request, response) => {
      response.writeHead(200, { "content-type": "text/event-stream" });
      request.on("close", () => {
        closedByClient = true;
      });
      const parts = ["하나", "둘", "셋"];
      let index = 0;
      const timer = setInterval(() => {
        if (index < parts.length) {
          response.write(chunk(parts[index] ?? ""));
          index += 1;
          return;
        }
        clearInterval(timer);
        response.end("data: [DONE]\n\n");
      }, 20);
      response.on("close", () => clearInterval(timer));
    });
    await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
    const address = server.address();
    if (address === null || typeof address === "string") throw new Error("주소 없음");
    baseUrl = `http://127.0.0.1:${address.port}/v1`;
  });

  afterAll(async () => {
    await new Promise<void>((resolve) => server.close(() => resolve()));
  });

  it("전역 fetch로 조각을 순서대로 받는다", async () => {
    const signal = new AbortController().signal;
    const deltas = await collect(
      streamOpenAiCompatible(REQUEST, { baseUrl, model: "m", apiKey: null, signal }),
    );

    expect(deltas).toEqual(["하나", "둘", "셋"]);
  });

  it("AbortController로 중간에 끊으면 AbortError가 나고 서버 연결이 닫힌다", async () => {
    closedByClient = false;
    const controller = new AbortController();
    const received: string[] = [];

    await expect(
      (async () => {
        for await (const delta of streamOpenAiCompatible(REQUEST, {
          baseUrl,
          model: "m",
          apiKey: null,
          signal: controller.signal,
        })) {
          received.push(delta);
          controller.abort();
        }
      })(),
    ).rejects.toMatchObject({ name: "AbortError" });

    expect(received).toEqual(["하나"]);
    await vi.waitFor(() => expect(closedByClient).toBe(true));
  });
});
