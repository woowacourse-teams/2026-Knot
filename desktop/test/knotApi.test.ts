import { describe, expect, it, vi } from "vitest";

vi.mock("electron-log/main", () => ({
  default: {
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    transports: { file: { getFile: () => ({ path: "" }) }, console: {} },
  },
}));

const { createKnotApiClient, KnotApiError } = await import("../src/main/chat/knotApi");

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

const READY = {
  status: "READY",
  userMessageId: 101,
  groundingRules: "규칙\n\n",
  chunks: [
    { importRunId: 301, importedPageId: 201, chunkIndex: 2, title: "제목", sourceUrl: "https://n.so/1", content: "본문", score: 0.9 },
  ],
};

function client(fetchImpl: typeof fetch, token: string | null = "jwt", timeoutMs?: number) {
  return createKnotApiClient({ apiOrigin: "http://localhost:8080", readToken: () => token, fetch: fetchImpl, timeoutMs });
}

const signal = () => new AbortController().signal;

describe("createKnotApiClient", () => {
  it("검색을 Bearer 헤더로 POST하고 READY 응답을 돌려준다", async () => {
    const fetchMock = vi.fn(async () => json(READY));

    const result = await client(fetchMock).search(7, "질문", signal());

    expect(result).toEqual(READY);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("http://localhost:8080/api/v1/conversations/7/search");
    expect(init.method).toBe("POST");
    expect(init.headers).toMatchObject({ Authorization: "Bearer jwt", "Content-Type": "application/json" });
    expect(JSON.parse(init.body as string)).toEqual({ content: "질문" });
  });

  it("READY가 아닌 응답도 계약대로 돌려준다", async () => {
    const body = { status: "NO_RESULT", userMessageId: 1, assistantMessageId: 2, fallbackAnswer: "못 찾음" };
    const fetchMock = vi.fn(async () => json(body));

    expect(await client(fetchMock).search(7, "질문", signal())).toEqual(body);
  });

  it("서버 HTTP 오류는 본문의 code·message를 그대로 KnotApiError로 옮긴다", async () => {
    const fetchMock = vi.fn(async () =>
      json({ code: "CHAT_TURN_IN_PROGRESS", message: "이전 질문의 답변이 아직 진행 중입니다" }, 409),
    );

    await expect(client(fetchMock).search(7, "질문", signal())).rejects.toMatchObject({
      status: 409,
      code: "CHAT_TURN_IN_PROGRESS",
      message: "이전 질문의 답변이 아직 진행 중입니다",
    });
  });

  it("본문을 읽지 못한 HTTP 오류는 UNKNOWN (Q45)", async () => {
    const fetchMock = vi.fn(async () => new Response("", { status: 502 }));

    await expect(client(fetchMock).search(7, "질문", signal())).rejects.toMatchObject({ status: 502, code: "UNKNOWN" });
  });

  it("저장된 토큰이 없으면 요청 없이 UNAUTHENTICATED", async () => {
    const fetchMock = vi.fn();

    await expect(client(fetchMock, null).search(7, "질문", signal())).rejects.toMatchObject({
      code: "UNAUTHENTICATED",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("네트워크 오류·타임아웃은 LLM_STREAM_FAILED, 호출자 취소는 AbortError", async () => {
    const failing = vi.fn(async () => {
      throw new TypeError("fetch failed");
    });
    await expect(client(failing).fetchMessages(7, signal())).rejects.toMatchObject({ code: "LLM_STREAM_FAILED" });

    const hanging = vi.fn((_url: string, init: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        init.signal?.addEventListener("abort", () => reject(init.signal?.reason));
      }),
    );
    await expect(client(hanging as unknown as typeof fetch, "jwt", 20).fetchMessages(7, signal())).rejects.toMatchObject({
      code: "LLM_STREAM_FAILED",
    });

    const controller = new AbortController();
    const pending = client(hanging as unknown as typeof fetch).fetchMessages(7, controller.signal);
    controller.abort();
    await expect(pending).rejects.toMatchObject({ name: "AbortError" });
  });

  it("이력을 id·role·content만 남겨 돌려주고 모양이 다르면 실패한다", async () => {
    const fetchMock = vi.fn(async () =>
      json([
        { id: 1, role: "USER", content: "q", createdAt: "2026-09-08T00:00:00Z" },
        { id: 2, role: "ASSISTANT", content: "a", createdAt: "2026-09-08T00:00:01Z" },
      ]),
    );
    expect(await client(fetchMock).fetchMessages(7, signal())).toEqual([
      { id: 1, role: "USER", content: "q" },
      { id: 2, role: "ASSISTANT", content: "a" },
    ]);

    const malformed = vi.fn(async () => json([{ id: "1", role: "SYSTEM" }]));
    await expect(client(malformed).fetchMessages(7, signal())).rejects.toMatchObject({ code: "LLM_STREAM_FAILED" });
  });

  it("답변 저장은 계약 본문을 POST하고 messageId를 돌려준다", async () => {
    const fetchMock = vi.fn(async () => json({ messageId: 55 }, 201));
    const body = {
      userMessageId: 101,
      content: "답",
      references: [{ importRunId: 301, importedPageId: 201, chunkIndex: 2, score: 0.9 }],
    };

    expect(await client(fetchMock).saveAssistantMessage(7, body, signal())).toEqual({ messageId: 55 });
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("http://localhost:8080/api/v1/conversations/7/messages/assistant");
    expect(JSON.parse(init.body as string)).toEqual(body);
  });

  it("KnotApiError는 Error다", () => {
    expect(new KnotApiError(403, "CHAT_ACCESS_DENIED", "x")).toBeInstanceOf(Error);
  });
});
