import { describe, expect, it, vi } from "vitest";
import { collect, sseResponse } from "./helpers/sseResponse";

const logger = { info: vi.fn(), warn: vi.fn(), error: vi.fn() };
vi.mock("electron-log/main", () => ({
  default: { ...logger, transports: { file: { getFile: () => ({ path: "" }) }, console: {} } },
}));

const { streamAnthropic } = await import("../src/main/llm/anthropicClient");

const REQUEST = { system: "규칙", messages: [{ role: "user" as const, content: "질문" }] };

function options(fetchImpl: typeof fetch, apiKey: string | null = "sk-ant-x") {
  return {
    baseUrl: "https://api.anthropic.com",
    model: "claude-sonnet-5",
    apiKey,
    signal: new AbortController().signal,
    fetch: fetchImpl,
  };
}

const event = (type: string, body: Record<string, unknown>) =>
  `event: ${type}\ndata: ${JSON.stringify({ type, ...body })}\n\n`;
const textDelta = (text: string) => event("content_block_delta", { index: 0, delta: { type: "text_delta", text } });

describe("streamAnthropic", () => {
  it("헤더·본문을 최소 필드로 보내고 text_delta만 조각으로 넘긴다 (Q43)", async () => {
    const fetchMock = vi.fn(async () =>
      sseResponse([
        event("message_start", { message: { model: "claude-sonnet-5", usage: { input_tokens: 12, cache_read_input_tokens: 0 } } }),
        event("content_block_start", { index: 0, content_block: { type: "text", text: "" } }),
        event("ping", {}),
        textDelta("안"),
        event("content_block_delta", { index: 0, delta: { type: "thinking_delta", thinking: "…" } }),
        textDelta("녕"),
        event("content_block_stop", { index: 0 }),
        event("message_delta", { delta: { stop_reason: "end_turn" }, usage: { output_tokens: 3 } }),
        event("message_stop", {}),
      ]),
    );

    const deltas = await collect(streamAnthropic(REQUEST, options(fetchMock)));

    expect(deltas).toEqual(["안", "녕"]);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("https://api.anthropic.com/v1/messages");
    expect(init.headers).toMatchObject({ "x-api-key": "sk-ant-x", "anthropic-version": "2023-06-01" });
    expect(JSON.parse(init.body as string)).toEqual({
      model: "claude-sonnet-5",
      max_tokens: 4096,
      stream: true,
      system: "규칙",
      messages: [{ role: "user", content: "질문" }],
    });
    // 사용량은 INFO로만 남긴다. 키·본문은 없다
    expect(logger.info).toHaveBeenCalledWith(
      "[knot] Anthropic 사용량",
      expect.objectContaining({ model: "claude-sonnet-5", inputTokens: 12, outputTokens: 3 }),
    );
    expect(JSON.stringify(logger.info.mock.calls)).not.toContain("sk-ant-x");
  });

  it("키가 없으면 요청 전에 LLM_CONFIGURATION_INVALID", async () => {
    const fetchMock = vi.fn();

    await expect(collect(streamAnthropic(REQUEST, options(fetchMock, null)))).rejects.toMatchObject({
      code: "LLM_CONFIGURATION_INVALID",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("stop_reason=refusal은 LLM_REFUSED", async () => {
    const fetchMock = vi.fn(async () =>
      sseResponse([
        textDelta("일부"),
        event("message_delta", { delta: { stop_reason: "refusal", stop_details: { category: "x" } }, usage: { output_tokens: 1 } }),
        event("message_stop", {}),
      ]),
    );

    await expect(collect(streamAnthropic(REQUEST, options(fetchMock)))).rejects.toMatchObject({ code: "LLM_REFUSED" });
  });

  it("스트림 error 이벤트는 type으로 매핑한다", async () => {
    for (const [type, code] of [
      ["overloaded_error", "LLM_RATE_LIMITED"],
      ["rate_limit_error", "LLM_RATE_LIMITED"],
      ["authentication_error", "LLM_CONFIGURATION_INVALID"],
      ["api_error", "LLM_STREAM_FAILED"],
    ] as const) {
      const fetchMock = vi.fn(async () => sseResponse([event("error", { error: { type, message: "…" } })]));
      await expect(collect(streamAnthropic(REQUEST, options(fetchMock)))).rejects.toMatchObject({ code });
    }
  });

  it("HTTP 상태를 코드로 매핑하고 본문은 type만 로그에 남긴다", async () => {
    for (const [status, code] of [
      [401, "LLM_CONFIGURATION_INVALID"],
      [429, "LLM_RATE_LIMITED"],
      [529, "LLM_RATE_LIMITED"],
      [500, "LLM_STREAM_FAILED"],
    ] as const) {
      const fetchMock = vi.fn(
        async () =>
          new Response(JSON.stringify({ type: "error", error: { type: "some_error", message: "비밀 본문" } }), { status }),
      );
      await expect(collect(streamAnthropic(REQUEST, options(fetchMock)))).rejects.toMatchObject({ code });
    }
    expect(JSON.stringify(logger.warn.mock.calls)).not.toContain("비밀 본문");
  });

  it("message_stop 없이 끊기면 LLM_STREAM_FAILED", async () => {
    const fetchMock = vi.fn(async () => sseResponse([textDelta("답")]));

    await expect(collect(streamAnthropic(REQUEST, options(fetchMock)))).rejects.toMatchObject({ code: "LLM_STREAM_FAILED" });
  });
});
