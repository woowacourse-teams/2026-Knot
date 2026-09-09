import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ChatMessageSummary, KnotApiClient, WorkspaceSearchResponse } from "../src/main/chat/knotApi";
import type { MessagesClient, MessagesStreamRequest } from "../src/main/llm/messagesClient";
import { logMock } from "./helpers/logMock";

vi.mock("electron-log/main", () => ({ default: logMock }));

const { KnotApiError } = await import("../src/main/chat/knotApi");
const { MessagesApiError } = await import("../src/main/llm/messagesClient");
const { ANSWER_ERRORS, AnswerInputError, createAnswerFlow, parseAnswerStreamInput, toAnswerRequest } =
  await import("../src/main/llm/answerFlow");

const READY: WorkspaceSearchResponse = {
  status: "READY",
  groundingRules: "규칙\n\n",
  chunks: [
    { importRunId: 301, importedPageId: 201, chunkIndex: 2, title: "제목", sourceUrl: "https://n.so/1", content: "본문", score: 0.9 },
    { importRunId: 301, importedPageId: 202, chunkIndex: 0, title: "둘째", sourceUrl: "https://n.so/2", content: "본문2", score: 0.8 },
  ],
};
const HISTORY: ChatMessageSummary[] = [
  { id: 1, role: "USER", content: "이전 질문", createdAt: "2026-09-09T00:00:00Z" },
  { id: 2, role: "ASSISTANT", content: "이전 답변", createdAt: "2026-09-09T00:00:01Z" },
];
const REQUEST = { workspaceId: 7, sessionId: 42, content: "질문 SECRET-Q" };

function harness(options: { accessToken?: string | null; search?: WorkspaceSearchResponse } = {}) {
  const api = {
    listWorkspaces: vi.fn(),
    searchWorkspace: vi.fn(async () => options.search ?? READY),
    createConversation: vi.fn(),
    saveTurn: vi.fn(async () => ({ userMessageId: 10, messageId: 11 })),
    listMessages: vi.fn(async () => HISTORY),
  } satisfies KnotApiClient;
  const subscription = {
    getAccessToken: vi.fn(async () => (options.accessToken === undefined ? "sk-ant-oat01-SECRET" : options.accessToken)),
    recordAnswer: vi.fn(),
  };
  const messages = {
    stream: vi.fn<MessagesClient["stream"]>(async (_request, onDelta) => {
      onDelta("안녕");
      onDelta("하세요");
      return { text: "안녕하세요", stopReason: "end_turn", usage: { inputTokens: 1, outputTokens: 2 }, model: "claude-fable-5-1" };
    }),
  };
  const events = { chunk: vi.fn(), complete: vi.fn(), error: vi.fn() };
  const flow = createAnswerFlow({
    api,
    subscription,
    settings: { read: () => ({ model: "claude-fable-5-1", effort: "high" }) },
    messages,
    now: () => 0,
  });
  return { api, subscription, messages, events, flow };
}

beforeEach(() => {
  logMock.info.mockReset();
  logMock.warn.mockReset();
});

describe("run — 정상 경로", () => {
  it("검색(S7) → 히스토리 → 구독 호출 → 턴 저장(S2) 순으로 진행하고 chunk·complete를 낸다", async () => {
    const h = harness();

    await h.flow.run(REQUEST, h.events, new AbortController().signal);

    expect(h.api.searchWorkspace).toHaveBeenCalledWith(7, "질문 SECRET-Q", expect.any(AbortSignal));
    expect(h.api.listMessages).toHaveBeenCalledWith(42, expect.any(AbortSignal));
    const request = h.messages.stream.mock.calls[0]![0] as MessagesStreamRequest;
    expect(request).toMatchObject({ accessToken: "sk-ant-oat01-SECRET", model: "claude-fable-5-1", effort: "high", maxTokens: 4096 });
    expect(request.system).toBe(
      "규칙\n\n[근거 문서 1]\n제목: 제목\n문서 ID: 201\n문서 링크: https://n.so/1\n내용:\n본문\n\n" +
        "[근거 문서 2]\n제목: 둘째\n문서 ID: 202\n문서 링크: https://n.so/2\n내용:\n본문2\n\n",
    );
    expect(request.messages).toEqual([
      { role: "user", content: "이전 질문" },
      { role: "assistant", content: "이전 답변" },
      { role: "user", content: "질문 SECRET-Q" },
    ]);
    expect(h.api.saveTurn).toHaveBeenCalledWith(
      42,
      {
        question: "질문 SECRET-Q",
        answer: "안녕하세요",
        references: [
          { importRunId: 301, importedPageId: 201, chunkIndex: 2, score: 0.9 },
          { importRunId: 301, importedPageId: 202, chunkIndex: 0, score: 0.8 },
        ],
      },
      expect.any(AbortSignal),
    );
    expect(h.events.chunk.mock.calls.map((call) => call[0])).toEqual(["안녕", "하세요"]);
    expect(h.events.complete).toHaveBeenCalledWith(11);
    expect(h.events.error).not.toHaveBeenCalled();
    expect(h.subscription.recordAnswer).toHaveBeenCalledWith("subscription", null);
  });

  it("READY가 아니면 모델을 부르지 않고 fallbackAnswer를 references 없이 저장한 뒤 흘린다", async () => {
    const h = harness({ search: { status: "NO_RESULT", fallbackAnswer: "못 찾았어요" } });

    await h.flow.run(REQUEST, h.events, new AbortController().signal);

    expect(h.messages.stream).not.toHaveBeenCalled();
    expect(h.api.listMessages).not.toHaveBeenCalled();
    expect(h.api.saveTurn).toHaveBeenCalledWith(42, { question: "질문 SECRET-Q", answer: "못 찾았어요", references: [] }, expect.any(AbortSignal));
    expect(h.events.chunk).toHaveBeenCalledWith("못 찾았어요");
    expect(h.events.complete).toHaveBeenCalledWith(11);
  });
});

describe("run — 폴백(Q66)", () => {
  it("구독 미로그인이면 검색 전에 fallback:true로 끝난다", async () => {
    const h = harness({ accessToken: null });

    await h.flow.run(REQUEST, h.events, new AbortController().signal);

    expect(h.api.searchWorkspace).not.toHaveBeenCalled();
    expect(h.events.error).toHaveBeenCalledWith({ ...ANSWER_ERRORS.notSignedIn, fallback: true });
    expect(h.subscription.recordAnswer).toHaveBeenCalledWith("server-sse", "SUBSCRIPTION_NOT_SIGNED_IN");
  });

  it("첫 chunk 전의 모델 쪽 실패는 fallback:true, 저장하지 않는다", async () => {
    const h = harness();
    h.messages.stream.mockRejectedValueOnce(new MessagesApiError(429, "SUBSCRIPTION_RATE_LIMITED", "request"));

    await h.flow.run(REQUEST, h.events, new AbortController().signal);

    expect(h.events.error).toHaveBeenCalledWith(expect.objectContaining({ code: "SUBSCRIPTION_RATE_LIMITED", fallback: true }));
    expect(h.api.saveTurn).not.toHaveBeenCalled();
    expect(h.subscription.recordAnswer).toHaveBeenCalledWith("server-sse", "SUBSCRIPTION_RATE_LIMITED");
  });

  it("첫 chunk 뒤의 스트림 실패는 fallback:false다(부분 답변이 화면에 있다)", async () => {
    const h = harness();
    h.messages.stream.mockImplementationOnce(async (_request, onDelta) => {
      onDelta("부분");
      throw new MessagesApiError(null, "LLM_STREAM_TIMEOUT", "stream");
    });

    await h.flow.run(REQUEST, h.events, new AbortController().signal);

    expect(h.events.chunk).toHaveBeenCalledWith("부분");
    expect(h.events.error).toHaveBeenCalledWith(expect.objectContaining({ code: "LLM_STREAM_TIMEOUT", fallback: false }));
    expect(h.api.saveTurn).not.toHaveBeenCalled();
    expect(h.subscription.recordAnswer).toHaveBeenCalledWith("subscription", "LLM_STREAM_TIMEOUT");
  });

  it("빈 답변은 실패로 본다", async () => {
    const h = harness();
    h.messages.stream.mockResolvedValueOnce({ text: "", stopReason: "end_turn", usage: { inputTokens: 0, outputTokens: 0 }, model: "m" });

    await h.flow.run(REQUEST, h.events, new AbortController().signal);

    expect(h.events.error).toHaveBeenCalledWith({ ...ANSWER_ERRORS.emptyAnswer, fallback: true });
    expect(h.api.saveTurn).not.toHaveBeenCalled();
  });
});

describe("run — Knot 서버 오류(폴백 없음)", () => {
  it("검색 오류는 서버 코드·문구 그대로 fallback:false다", async () => {
    const h = harness();
    h.api.searchWorkspace.mockRejectedValueOnce(new KnotApiError(409, "CHAT_DOCUMENTS_NOT_READY", "문서 준비 중"));

    await h.flow.run(REQUEST, h.events, new AbortController().signal);

    expect(h.events.error).toHaveBeenCalledWith({ code: "CHAT_DOCUMENTS_NOT_READY", message: "문서 준비 중", fallback: false });
    expect(h.messages.stream).not.toHaveBeenCalled();
    expect(h.subscription.recordAnswer).not.toHaveBeenCalled();
  });

  it("턴 저장 실패는 답변을 흘린 뒤 fallback:false로 끝난다", async () => {
    const h = harness();
    h.api.saveTurn.mockRejectedValueOnce(new KnotApiError(409, "CHAT_TURN_IN_PROGRESS", "진행 중"));

    await h.flow.run(REQUEST, h.events, new AbortController().signal);

    expect(h.events.chunk).toHaveBeenCalledTimes(2);
    expect(h.events.complete).not.toHaveBeenCalled();
    expect(h.events.error).toHaveBeenCalledWith({ code: "CHAT_TURN_IN_PROGRESS", message: "진행 중", fallback: false });
    expect(h.subscription.recordAnswer).toHaveBeenCalledWith("subscription", "CHAT_TURN_IN_PROGRESS");
  });

  it("Knot 토큰이 없으면 UNAUTHENTICATED를 그대로 낸다", async () => {
    const h = harness();
    h.api.searchWorkspace.mockRejectedValueOnce(new KnotApiError(401, "UNAUTHENTICATED", "Knot 앱에 로그인하세요"));

    await h.flow.run(REQUEST, h.events, new AbortController().signal);

    expect(h.events.error).toHaveBeenCalledWith({ code: "UNAUTHENTICATED", message: "Knot 앱에 로그인하세요", fallback: false });
  });
});

describe("run — 취소·로그", () => {
  it("스트림 중 취소되면 이벤트 없이 끝나고 저장하지 않는다", async () => {
    const h = harness();
    const controller = new AbortController();
    h.messages.stream.mockImplementationOnce(async (_request, onDelta, signal) => {
      onDelta("부분");
      controller.abort();
      const error = new Error("aborted");
      error.name = "AbortError";
      expect(signal.aborted).toBe(true);
      throw error;
    });

    await h.flow.run(REQUEST, h.events, controller.signal);

    expect(h.events.error).not.toHaveBeenCalled();
    expect(h.events.complete).not.toHaveBeenCalled();
    expect(h.api.saveTurn).not.toHaveBeenCalled();
  });

  it("로그에 질문·답변·구독 토큰이 남지 않는다", async () => {
    const h = harness();
    await h.flow.run(REQUEST, h.events, new AbortController().signal);
    h.messages.stream.mockRejectedValueOnce(new MessagesApiError(401, "SUBSCRIPTION_UNAUTHORIZED", "request"));
    await h.flow.run(REQUEST, h.events, new AbortController().signal);

    const logged = JSON.stringify([...logMock.info.mock.calls, ...logMock.warn.mock.calls]);
    expect(logged).not.toContain("SECRET");
    expect(logged).not.toContain("안녕");
    expect(logged).toContain("sessionId");
  });
});

describe("parseAnswerStreamInput", () => {
  it("올바른 입력을 다듬어 돌려주고 숫자로 바꾼다", () => {
    const input = parseAnswerStreamInput({ requestId: "abc-123", workspaceId: "7", sessionId: "42", content: "  질문  " });
    expect(input).toEqual({ requestId: "abc-123", workspaceId: "7", sessionId: "42", content: "질문" });
    expect(toAnswerRequest(input)).toEqual({ workspaceId: 7, sessionId: 42, content: "질문" });
  });

  it.each([
    ["객체 아님", "x"],
    ["requestId 없음", { workspaceId: "7", sessionId: "42", content: "q" }],
    ["requestId 특수문자", { requestId: "a b", workspaceId: "7", sessionId: "42", content: "q" }],
    ["workspaceId 0", { requestId: "a", workspaceId: "0", sessionId: "42", content: "q" }],
    ["sessionId 숫자형", { requestId: "a", workspaceId: "7", sessionId: 42, content: "q" }],
    ["content 공백", { requestId: "a", workspaceId: "7", sessionId: "42", content: "   " }],
    ["content 초과", { requestId: "a", workspaceId: "7", sessionId: "42", content: "가".repeat(10_001) }],
  ])("잘못된 입력은 거절한다: %s", (_label, raw) => {
    expect(() => parseAnswerStreamInput(raw)).toThrow(AnswerInputError);
  });
});
