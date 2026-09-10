import { beforeEach, describe, expect, it, vi } from "vitest";
import { logMock } from "./helpers/logMock";

vi.mock("electron-log/main", () => ({ default: logMock }));

const { ANTHROPIC_BETA, ANTHROPIC_MESSAGES_URL, ANTHROPIC_VERSION, CLAUDE_CODE_SYSTEM_IDENTITY, MessagesApiError, createMessagesClient } =
  await import("../src/main/llm/messagesClient");

const REQUEST = {
  accessToken: "sk-ant-oat01-SECRET",
  model: "claude-fable-5-1",
  effort: "high" as const,
  maxTokens: 4096,
  system: "규칙 SECRET-SYSTEM",
  messages: [{ role: "user" as const, content: "질문 SECRET-QUESTION" }],
};

const frame = (event: string, data: object): string => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;

const OK_FRAMES = [
  frame("message_start", { type: "message_start", message: { model: "claude-fable-5-1-x", usage: { input_tokens: 120 } } }),
  frame("content_block_start", { type: "content_block_start", index: 0, content_block: { type: "text", text: "" } }),
  frame("ping", { type: "ping" }),
  frame("content_block_delta", { type: "content_block_delta", index: 0, delta: { type: "text_delta", text: "안녕" } }),
  frame("content_block_delta", { type: "content_block_delta", index: 0, delta: { type: "thinking_delta", thinking: "x" } }),
  frame("content_block_delta", { type: "content_block_delta", index: 0, delta: { type: "text_delta", text: "하세요" } }),
  frame("content_block_stop", { type: "content_block_stop", index: 0 }),
  frame("message_delta", { type: "message_delta", delta: { stop_reason: "end_turn" }, usage: { output_tokens: 7 } }),
  frame("message_stop", { type: "message_stop" }),
];

function sseResponse(chunks: string[], status = 200): Response {
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk));
      controller.close();
    },
  });
  return new Response(body, { status, headers: { "Content-Type": "text/event-stream" } });
}

/** 닫히지 않는 스트림. 이벤트 사이 타임아웃 검증용 */
function hangingResponse(chunks: string[]): Response {
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk));
    },
  });
  return new Response(body, { status: 200 });
}

const fetchMock = vi.fn<typeof fetch>();
const signal = () => new AbortController().signal;

beforeEach(() => {
  fetchMock.mockReset();
  logMock.info.mockReset();
  logMock.warn.mockReset();
});

function client(options: { headersTimeoutMs?: number; idleTimeoutMs?: number } = {}) {
  return createMessagesClient({ fetch: fetchMock, ...options });
}

describe("stream", () => {
  it("Q61 c의 헤더와 Q62의 본문으로 POST하고 text_delta만 흘린 뒤 사용량을 돌려준다", async () => {
    fetchMock.mockResolvedValueOnce(sseResponse(OK_FRAMES));
    const deltas: string[] = [];

    const result = await client().stream(REQUEST, (delta) => deltas.push(delta), signal());

    expect(deltas).toEqual(["안녕", "하세요"]);
    expect(result).toEqual({
      text: "안녕하세요",
      stopReason: "end_turn",
      usage: { inputTokens: 120, outputTokens: 7 },
      model: "claude-fable-5-1-x",
    });
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe(ANTHROPIC_MESSAGES_URL);
    expect(init?.method).toBe("POST");
    expect(init?.headers).toEqual({
      Authorization: "Bearer sk-ant-oat01-SECRET",
      "anthropic-version": ANTHROPIC_VERSION,
      "anthropic-beta": ANTHROPIC_BETA,
      "user-agent": "claude-cli/2.1.263",
      "x-app": "cli",
      "anthropic-dangerous-direct-browser-access": "true",
      "Content-Type": "application/json",
      Accept: "text/event-stream",
    });
    expect(ANTHROPIC_BETA).toBe("claude-code-20250219,oauth-2025-04-20");
    const body = JSON.parse(String(init?.body)) as Record<string, unknown>;
    expect(body).toEqual({
      model: "claude-fable-5-1",
      max_tokens: 4096,
      stream: true,
      system: [
        { type: "text", text: "You are Claude Code, Anthropic's official CLI for Claude." },
        { type: "text", text: "규칙 SECRET-SYSTEM" },
      ],
      messages: [{ role: "user", content: "질문 SECRET-QUESTION" }],
      output_config: { effort: "high" },
    });
    expect(body).not.toHaveProperty("temperature");
    expect(CLAUDE_CODE_SYSTEM_IDENTITY).toBe("You are Claude Code, Anthropic's official CLI for Claude.");
  });

  it("429 본문 message를 로그에 남겨 식별 블록 게이트(Error)와 실제 한도를 구분한다(Q66)", async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ type: "error", error: { type: "rate_limit_error", message: "Error" } }), { status: 429 }),
    );

    await client()
      .stream(REQUEST, () => {}, signal())
      .catch(() => {});

    expect(logMock.warn).toHaveBeenCalledWith(
      "[knot] 구독 모델 HTTP 오류",
      expect.objectContaining({ model: "claude-fable-5-1", status: 429, type: "rate_limit_error", message: "Error" }),
    );
  });

  it("오류 본문 message는 200자까지만 로그에 남긴다", async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ type: "error", error: { type: "api_error", message: "x".repeat(500) } }), { status: 500 }),
    );

    await client()
      .stream(REQUEST, () => {}, signal())
      .catch(() => {});

    const call = logMock.warn.mock.calls.find((args) => args[0] === "[knot] 구독 모델 HTTP 오류");
    expect((call?.[1] as { message: string }).message).toHaveLength(200);
  });

  it("청크 경계가 프레임 중간·한글 바이트 중간에 걸려도 읽는다", async () => {
    const text = OK_FRAMES.join("");
    const bytes = new TextEncoder().encode(text);
    const cut = text.indexOf("안녕") + 1; // 문자 기준이라 바이트로는 한글 중간을 자른다
    const cutBytes = new TextEncoder().encode(text.slice(0, cut)).length + 1;
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(bytes.slice(0, cutBytes));
        controller.enqueue(bytes.slice(cutBytes));
        controller.close();
      },
    });
    fetchMock.mockResolvedValueOnce(new Response(body, { status: 200 }));

    const result = await client().stream(REQUEST, () => {}, signal());

    expect(result.text).toBe("안녕하세요");
  });

  it.each([
    [401, "authentication_error", "SUBSCRIPTION_UNAUTHORIZED"],
    [403, "permission_error", "SUBSCRIPTION_UNAUTHORIZED"],
    [429, "rate_limit_error", "SUBSCRIPTION_RATE_LIMITED"],
    [529, "overloaded_error", "SUBSCRIPTION_RATE_LIMITED"],
    [400, "invalid_request_error", "SUBSCRIPTION_REQUEST_REJECTED"],
    [500, "api_error", "LLM_STREAM_FAILED"],
  ])("HTTP %s(%s)는 request 단계 %s다", async (status, type, code) => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ type: "error", error: { type, message: "SECRET-DETAIL" } }), { status }),
    );

    const error = (await client()
      .stream(REQUEST, () => {}, signal())
      .catch((caught: unknown) => caught)) as InstanceType<typeof MessagesApiError>;

    expect(error).toBeInstanceOf(MessagesApiError);
    expect(error.phase).toBe("request");
    expect(error.status).toBe(status);
    expect(error.code).toBe(code);
    expect(error.errorType).toBe(type);
    expect(error.message).not.toContain("SECRET");
  });

  it("네트워크 오류는 SUBSCRIPTION_UNREACHABLE이다", async () => {
    fetchMock.mockRejectedValueOnce(new TypeError("fetch failed"));

    const error = (await client()
      .stream(REQUEST, () => {}, signal())
      .catch((caught: unknown) => caught)) as InstanceType<typeof MessagesApiError>;

    expect(error.code).toBe("SUBSCRIPTION_UNREACHABLE");
    expect(error.phase).toBe("request");
  });

  it("응답 헤더가 제한 안에 오지 않으면 SUBSCRIPTION_UNREACHABLE이다", async () => {
    fetchMock.mockImplementationOnce(
      (_url, init) =>
        new Promise((_resolve, reject) => {
          init?.signal?.addEventListener("abort", () => reject(new Error("aborted")));
        }),
    );

    const error = (await client({ headersTimeoutMs: 20 })
      .stream(REQUEST, () => {}, signal())
      .catch((caught: unknown) => caught)) as InstanceType<typeof MessagesApiError>;

    expect(error.code).toBe("SUBSCRIPTION_UNREACHABLE");
  });

  it("스트림 error 이벤트는 stream 단계 코드로 분류한다", async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse([
        OK_FRAMES[0]!,
        frame("error", { type: "error", error: { type: "overloaded_error", message: "Overloaded" } }),
      ]),
    );

    const error = (await client()
      .stream(REQUEST, () => {}, signal())
      .catch((caught: unknown) => caught)) as InstanceType<typeof MessagesApiError>;

    expect(error.phase).toBe("stream");
    expect(error.code).toBe("LLM_RATE_LIMITED");
    expect(error.errorType).toBe("overloaded_error");
  });

  it("stop_reason이 refusal이면 LLM_REFUSED다", async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse([
        OK_FRAMES[0]!,
        frame("message_delta", { type: "message_delta", delta: { stop_reason: "refusal" }, usage: { output_tokens: 0 } }),
        frame("message_stop", { type: "message_stop" }),
      ]),
    );

    const error = (await client()
      .stream(REQUEST, () => {}, signal())
      .catch((caught: unknown) => caught)) as InstanceType<typeof MessagesApiError>;

    expect(error.code).toBe("LLM_REFUSED");
  });

  it("message_stop 없이 끝나면 LLM_STREAM_FAILED다(부분 텍스트는 흘러갔다)", async () => {
    fetchMock.mockResolvedValueOnce(sseResponse(OK_FRAMES.slice(0, 4)));
    const deltas: string[] = [];

    const error = (await client()
      .stream(REQUEST, (delta) => deltas.push(delta), signal())
      .catch((caught: unknown) => caught)) as InstanceType<typeof MessagesApiError>;

    expect(deltas).toEqual(["안녕"]);
    expect(error.code).toBe("LLM_STREAM_FAILED");
    expect(error.phase).toBe("stream");
  });

  it("이벤트 사이 대기가 제한을 넘으면 LLM_STREAM_TIMEOUT이다", async () => {
    fetchMock.mockResolvedValueOnce(hangingResponse(OK_FRAMES.slice(0, 4)));

    const error = (await client({ idleTimeoutMs: 30 })
      .stream(REQUEST, () => {}, signal())
      .catch((caught: unknown) => caught)) as InstanceType<typeof MessagesApiError>;

    expect(error.code).toBe("LLM_STREAM_TIMEOUT");
  });

  it("호출자가 취소하면 그 abort 오류가 그대로 올라오고 MessagesApiError가 아니다", async () => {
    fetchMock.mockResolvedValueOnce(hangingResponse(OK_FRAMES.slice(0, 2)));
    const controller = new AbortController();

    const pending = client().stream(REQUEST, () => {}, controller.signal);
    await new Promise((resolve) => setTimeout(resolve, 10));
    controller.abort();

    const error = await pending.catch((caught: unknown) => caught);
    expect(error).not.toBeInstanceOf(MessagesApiError);
    expect(controller.signal.aborted).toBe(true);
  });

  it("로그에 구독 토큰·프롬프트·답변이 남지 않는다", async () => {
    fetchMock.mockResolvedValueOnce(sseResponse(OK_FRAMES));
    await client().stream(REQUEST, () => {}, signal());
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ error: { type: "x", message: "anthropic-detail" } }), { status: 500 }),
    );
    await client()
      .stream(REQUEST, () => {}, signal())
      .catch(() => {});

    const logged = JSON.stringify([...logMock.info.mock.calls, ...logMock.warn.mock.calls]);
    expect(logged).not.toContain("SECRET");
    expect(logged).not.toContain("안녕");
    expect(logged).toContain("outputTokens");
    // Anthropic이 쓴 오류 본문 message는 남긴다(Q66)
    expect(logged).toContain("anthropic-detail");
  });
});
