import { describe, expect, it, vi } from "vitest";
import type { ChatStreamEvent } from "../src/shared/api";
import type { ChatSearchResponse, KnotApiClient } from "../src/main/chat/knotApi";
import type { UserLlmStreamParams } from "../src/main/llm/userLlmClient";

vi.mock("electron-log/main", () => ({
  default: {
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    transports: { file: { getFile: () => ({ path: "" }) }, console: {} },
  },
}));

const { createChatCoordinator, describeConfigurationProblem } = await import("../src/main/chat/chatService");
const { KnotApiError } = await import("../src/main/chat/knotApi");
const { LlmError } = await import("../src/main/llm/errors");

const SETTINGS = { provider: "openai-compatible" as const, baseUrl: "http://localhost:1234/v1", model: "qwen" };

const READY: ChatSearchResponse = {
  status: "READY",
  userMessageId: 101,
  groundingRules: "규칙\n\n",
  chunks: [
    { importRunId: 301, importedPageId: 201, chunkIndex: 0, title: "A", sourceUrl: "https://n.so/a", content: "본문 A", score: 0.9 },
    { importRunId: 301, importedPageId: 202, chunkIndex: 3, title: "B", sourceUrl: "https://n.so/b", content: "본문 B", score: 0.8 },
  ],
};

const HISTORY = [
  { id: 100, role: "ASSISTANT" as const, content: "이전 답" },
  { id: 101, role: "USER" as const, content: "질문" },
];

/** 조각을 순서대로 내는 가짜 LLM. `deltas`가 함수면 매 조각마다 부른다(취소·지연 시나리오) */
function fakeLlm(deltas: string[] | (() => AsyncGenerator<string>)) {
  const calls: UserLlmStreamParams[] = [];
  const streamer = (params: UserLlmStreamParams): AsyncGenerator<string> => {
    calls.push(params);
    if (typeof deltas === "function") return deltas();
    return (async function* () {
      for (const delta of deltas) {
        if (params.signal.aborted) throw new DOMException("aborted", "AbortError");
        yield delta;
      }
    })();
  };
  return { streamer, calls };
}

function fakeApi(overrides: Partial<KnotApiClient> = {}): KnotApiClient & { saved: unknown[] } {
  const saved: unknown[] = [];
  return {
    saved,
    search: vi.fn(async () => READY),
    fetchMessages: vi.fn(async () => HISTORY),
    saveAssistantMessage: vi.fn(async (_sessionId: number, body: unknown) => {
      saved.push(body);
      return { messageId: 555 };
    }),
    ...overrides,
  };
}

function setup(options: {
  api?: KnotApiClient;
  llm?: ReturnType<typeof fakeLlm>;
  settings?: typeof SETTINGS;
  apiKey?: string | null;
  firstChunkTimeoutMs?: number;
} = {}) {
  const api = options.api ?? fakeApi();
  const llm = options.llm ?? fakeLlm(["안", "녕"]);
  let counter = 0;
  const coordinator = createChatCoordinator({
    api,
    readSettings: () => options.settings ?? SETTINGS,
    readApiKey: () => options.apiKey ?? null,
    streamLlm: llm.streamer,
    firstChunkTimeoutMs: options.firstChunkTimeoutMs,
    generateRequestId: () => `req-${++counter}`,
  });
  const events: Array<{ requestId: string; event: ChatStreamEvent }> = [];
  const sink = (requestId: string, event: ChatStreamEvent) => {
    events.push({ requestId, event });
  };
  return { coordinator, api, llm, events, sink };
}

describe("createChatCoordinator", () => {
  it("READY면 검색 → 이력 → LLM → 저장 순으로 부르고 chunk × n + complete를 낸다", async () => {
    const { coordinator, api, llm, events, sink } = setup();

    const handle = coordinator.ask({ sessionId: 7, content: "질문" }, sink);
    await handle.done;

    expect(handle.requestId).toBe("req-1");
    expect(events.map((entry) => entry.event)).toEqual([
      { event: "chunk", data: { delta: "안" } },
      { event: "chunk", data: { delta: "녕" } },
      { event: "complete", data: { messageId: 555 } },
    ]);
    expect(events.every((entry) => entry.requestId === "req-1")).toBe(true);

    // 프롬프트: 규칙 + 근거 블록, 이력 전체(마지막이 현재 질문)
    const request = llm.calls[0]?.request;
    expect(request?.system).toBe(
      "규칙\n\n" +
        "[근거 문서 1]\n제목: A\n문서 ID: 201\n문서 링크: https://n.so/a\n내용:\n본문 A\n\n" +
        "[근거 문서 2]\n제목: B\n문서 ID: 202\n문서 링크: https://n.so/b\n내용:\n본문 B\n\n",
    );
    expect(request?.messages).toEqual([
      { role: "assistant", content: "이전 답" },
      { role: "user", content: "질문" },
    ]);
    expect(llm.calls[0]?.settings).toEqual(SETTINGS);

    // 저장: 답변 전체 + 근거 4필드, 응답 순서가 rank
    expect(api.saveAssistantMessage).toHaveBeenCalledWith(
      7,
      {
        userMessageId: 101,
        content: "안녕",
        references: [
          { importRunId: 301, importedPageId: 201, chunkIndex: 0, score: 0.9 },
          { importRunId: 301, importedPageId: 202, chunkIndex: 3, score: 0.8 },
        ],
      },
      expect.any(AbortSignal),
    );
  });

  it("READY가 아니면 안내 문구 chunk 1개 + complete를 내고 LLM·저장을 부르지 않는다", async () => {
    const api = fakeApi({
      search: vi.fn(async () => ({
        status: "NEEDS_CLARIFICATION" as const,
        userMessageId: 1,
        assistantMessageId: 2,
        fallbackAnswer: "범위가 넓어요.",
      })),
    });
    const { coordinator, llm, events, sink } = setup({ api });

    await coordinator.ask({ sessionId: 7, content: "전부 알려줘" }, sink).done;

    expect(events.map((entry) => entry.event)).toEqual([
      { event: "chunk", data: { delta: "범위가 넓어요." } },
      { event: "complete", data: { messageId: 2 } },
    ]);
    expect(llm.calls).toHaveLength(0);
    expect(api.fetchMessages).not.toHaveBeenCalled();
    expect(api.saveAssistantMessage).not.toHaveBeenCalled();
  });

  it("설정이 비었거나 anthropic인데 키가 없으면 서버를 부르지 않고 LLM_CONFIGURATION_INVALID", async () => {
    const api = fakeApi();
    const { coordinator, events, sink } = setup({ api, settings: { ...SETTINGS, model: "" } });

    await coordinator.ask({ sessionId: 7, content: "질문" }, sink).done;

    expect(events.map((entry) => entry.event)).toEqual([
      { event: "error", data: { code: "LLM_CONFIGURATION_INVALID", message: expect.stringContaining("설정") } },
    ]);
    expect(api.search).not.toHaveBeenCalled();

    expect(describeConfigurationProblem({ ...SETTINGS, provider: "anthropic" }, null)).toMatch(/키/);
    expect(describeConfigurationProblem({ ...SETTINGS, provider: "anthropic" }, "sk")).toBeNull();
    expect(describeConfigurationProblem(SETTINGS, null)).toBeNull();
  });

  it("서버 HTTP 오류는 code·message를 그대로 중계한다", async () => {
    const api = fakeApi({
      search: vi.fn(async () => {
        throw new KnotApiError(409, "CHAT_DOCUMENTS_NOT_READY", "문서 동기화가 완료된 후 검색할 수 있습니다");
      }),
    });
    const { coordinator, events, sink } = setup({ api });

    await coordinator.ask({ sessionId: 7, content: "질문" }, sink).done;

    expect(events.map((entry) => entry.event)).toEqual([
      { event: "error", data: { code: "CHAT_DOCUMENTS_NOT_READY", message: "문서 동기화가 완료된 후 검색할 수 있습니다" } },
    ]);
  });

  it("LLM 오류는 코드 그대로, 그 밖의 예외는 LLM_STREAM_FAILED", async () => {
    const rateLimited = setup({
      llm: fakeLlm(() =>
        (async function* () {
          throw new LlmError("LLM_RATE_LIMITED");
        })(),
      ),
    });
    await rateLimited.coordinator.ask({ sessionId: 7, content: "질문" }, rateLimited.sink).done;
    expect(rateLimited.events.map((entry) => entry.event)).toEqual([
      { event: "error", data: { code: "LLM_RATE_LIMITED", message: expect.any(String) } },
    ]);
    expect(rateLimited.api.saveAssistantMessage).not.toHaveBeenCalled();

    const crashed = setup({
      api: fakeApi({
        fetchMessages: vi.fn(async () => {
          throw new Error("boom");
        }),
      }),
    });
    await crashed.coordinator.ask({ sessionId: 7, content: "질문" }, crashed.sink).done;
    expect(crashed.events.map((entry) => entry.event)).toEqual([
      { event: "error", data: { code: "LLM_STREAM_FAILED", message: "답변 생성에 실패했습니다" } },
    ]);
  });

  it("조각 0개로 끝나면 저장하지 않고 LLM_STREAM_FAILED (Q45)", async () => {
    const { coordinator, api, events, sink } = setup({ llm: fakeLlm([]) });

    await coordinator.ask({ sessionId: 7, content: "질문" }, sink).done;

    expect(events.map((entry) => entry.event)).toEqual([
      { event: "error", data: { code: "LLM_STREAM_FAILED", message: expect.any(String) } },
    ]);
    expect(api.saveAssistantMessage).not.toHaveBeenCalled();
  });

  it("같은 세션에 진행 중 요청이 있으면 CHAT_TURN_IN_PROGRESS를 내고 첫 요청은 그대로 진행한다", async () => {
    let release!: () => void;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    const { coordinator, events, sink } = setup({
      llm: fakeLlm(() =>
        (async function* () {
          await gate;
          yield "답";
        })(),
      ),
    });

    const first = coordinator.ask({ sessionId: 7, content: "질문 1" }, sink);
    const second = coordinator.ask({ sessionId: 7, content: "질문 2" }, sink);
    const other = coordinator.ask({ sessionId: 8, content: "다른 세션" }, sink);
    await second.done;
    release();
    await Promise.all([first.done, other.done]);

    expect(events.filter((entry) => entry.requestId === second.requestId).map((entry) => entry.event)).toEqual([
      { event: "error", data: { code: "CHAT_TURN_IN_PROGRESS", message: "이전 질문의 답변이 아직 진행 중입니다" } },
    ]);
    expect(events.filter((entry) => entry.requestId === first.requestId).map((entry) => entry.event.event)).toEqual([
      "chunk",
      "complete",
    ]);
    expect(events.filter((entry) => entry.requestId === other.requestId).map((entry) => entry.event.event)).toEqual([
      "chunk",
      "complete",
    ]);
  });

  it("첫 조각이 제한 시간 안에 오지 않으면 LLM 요청을 끊고 LLM_STREAM_TIMEOUT (Q26)", async () => {
    let seenSignal: AbortSignal | null = null;
    const { coordinator, api, events, sink } = setup({
      firstChunkTimeoutMs: 30,
      llm: {
        calls: [],
        streamer: (params) => {
          seenSignal = params.signal;
          return (async function* () {
            await new Promise<void>((_resolve, reject) => {
              params.signal.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")));
            });
            yield "늦은 답";
          })();
        },
      },
    });

    await coordinator.ask({ sessionId: 7, content: "질문" }, sink).done;

    expect(events.map((entry) => entry.event)).toEqual([
      { event: "error", data: { code: "LLM_STREAM_TIMEOUT", message: "답변 생성 시간이 초과되었습니다" } },
    ]);
    expect(seenSignal!.aborted).toBe(true);
    expect(api.saveAssistantMessage).not.toHaveBeenCalled();
  });

  it("취소하면 그 뒤 이벤트가 없고 저장하지 않으며 같은 세션에 다시 질문할 수 있다", async () => {
    let release!: () => void;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    let seenSignal: AbortSignal | null = null;
    const { coordinator, api, events, sink } = setup({
      llm: {
        calls: [],
        streamer: (params) => {
          seenSignal = params.signal;
          return (async function* () {
            yield "첫";
            await gate;
            if (params.signal.aborted) throw new DOMException("aborted", "AbortError");
            yield "둘";
          })();
        },
      },
    });

    const handle = coordinator.ask({ sessionId: 7, content: "질문" }, sink);
    await vi.waitFor(() => expect(events).toHaveLength(1));
    handle.cancel();
    release();
    await handle.done;

    expect(events.map((entry) => entry.event)).toEqual([{ event: "chunk", data: { delta: "첫" } }]);
    expect(seenSignal!.aborted).toBe(true);
    expect(api.saveAssistantMessage).not.toHaveBeenCalled();

    // 취소 뒤 같은 세션은 잠기지 않는다(같은 가짜 LLM이라 조각 둘 + complete)
    const again = coordinator.ask({ sessionId: 7, content: "다시" }, sink);
    await again.done;
    expect(events.filter((entry) => entry.requestId === again.requestId).map((entry) => entry.event.event)).toEqual([
      "chunk",
      "chunk",
      "complete",
    ]);
  });

  it("cancel(requestId)로도 끊을 수 있고 모르는 id는 무시한다", async () => {
    let release!: () => void;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    const { coordinator, events, sink } = setup({
      llm: fakeLlm(() =>
        (async function* () {
          yield "첫";
          await gate;
          yield "둘";
        })(),
      ),
    });

    const handle = coordinator.ask({ sessionId: 7, content: "질문" }, sink);
    await vi.waitFor(() => expect(events).toHaveLength(1));
    coordinator.cancel("없는-id");
    coordinator.cancel(handle.requestId);
    release();
    await handle.done;

    expect(events.map((entry) => entry.event.event)).toEqual(["chunk"]);
  });

  it("저장이 실패하면 error를 내고, 같은 세션의 다음 질문 직전에 한 번 다시 저장한다 (Q23·Q45)", async () => {
    const saveAssistantMessage = vi
      .fn()
      .mockRejectedValueOnce(new KnotApiError(500, "INTERNAL_SERVER_ERROR", "서버 오류"))
      .mockResolvedValue({ messageId: 777 });
    const api = fakeApi({ saveAssistantMessage });
    const { coordinator, events, sink } = setup({ api });

    const first = coordinator.ask({ sessionId: 7, content: "질문" }, sink);
    await first.done;
    expect(events.map((entry) => entry.event)).toEqual([
      { event: "chunk", data: { delta: "안" } },
      { event: "chunk", data: { delta: "녕" } },
      { event: "error", data: { code: "INTERNAL_SERVER_ERROR", message: "서버 오류" } },
    ]);

    events.length = 0;
    const second = coordinator.ask({ sessionId: 7, content: "다음 질문" }, sink);
    await second.done;

    // 재시도(같은 본문) → 이번 답변 저장 순
    expect(saveAssistantMessage).toHaveBeenCalledTimes(3);
    expect(saveAssistantMessage.mock.calls[1]?.[1]).toMatchObject({ userMessageId: 101, content: "안녕" });
    expect(events.map((entry) => entry.event.event)).toEqual(["chunk", "chunk", "complete"]);

    // 세 번째 질문에는 더 이상 재시도하지 않는다
    await coordinator.ask({ sessionId: 7, content: "셋째" }, sink).done;
    expect(saveAssistantMessage).toHaveBeenCalledTimes(4);
  });

  it("재저장이 거절돼도(턴 만료) 다음 질문은 진행한다", async () => {
    const saveAssistantMessage = vi
      .fn()
      .mockRejectedValueOnce(new KnotApiError(500, "INTERNAL_SERVER_ERROR", "서버 오류"))
      .mockRejectedValueOnce(new KnotApiError(409, "CHAT_TURN_MISMATCH", "턴 불일치"))
      .mockResolvedValue({ messageId: 9 });
    const api = fakeApi({ saveAssistantMessage });
    const { coordinator, events, sink } = setup({ api });

    await coordinator.ask({ sessionId: 7, content: "질문" }, sink).done;
    events.length = 0;
    await coordinator.ask({ sessionId: 7, content: "다음" }, sink).done;

    expect(events.map((entry) => entry.event)).toContainEqual({ event: "complete", data: { messageId: 9 } });
  });
});
