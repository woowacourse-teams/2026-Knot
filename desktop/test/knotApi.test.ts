import { describe, expect, it, vi } from "vitest";
import { logMock } from "./helpers/logMock";

vi.mock("electron-log/main", () => ({ default: logMock }));

const { KNOT_API_ERRORS, createKnotApiClient } = await import("../src/main/chat/knotApi");

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

const READY = {
  status: "READY",
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
  it("워크스페이스 목록을 Bearer 헤더로 GET하고 role은 있을 때만 옮긴다", async () => {
    const fetchMock = vi.fn(async () =>
      json({ lastViewedWorkspaceId: 1, workspaces: [{ id: 1, name: "팀" }, { id: 2, name: "개인", role: "OWNER" }] }),
    );

    const workspaces = await client(fetchMock).listWorkspaces(signal());

    expect(workspaces).toEqual([{ id: 1, name: "팀" }, { id: 2, name: "개인", role: "OWNER" }]);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("http://localhost:8080/api/v1/workspaces");
    expect(init.method).toBe("GET");
    expect(init.headers).toMatchObject({ Authorization: "Bearer jwt", Accept: "application/json" });
    expect(init.headers).not.toHaveProperty("Content-Type");
  });

  it("Workspace 검색을 POST하고 READY 응답을 돌려준다", async () => {
    const fetchMock = vi.fn(async () => json(READY));

    const result = await client(fetchMock).searchWorkspace(7, "질문", signal());

    expect(result).toEqual(READY);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("http://localhost:8080/api/v1/workspaces/7/search");
    expect(init.method).toBe("POST");
    expect(init.headers).toMatchObject({ Authorization: "Bearer jwt", "Content-Type": "application/json" });
    expect(JSON.parse(init.body as string)).toEqual({ content: "질문" });
  });

  it("세션 생성을 POST하고 id를 sessionId로 돌려준다(S10)", async () => {
    const fetchMock = vi.fn(async () =>
      json({ id: 12, title: "왜 PG?", createdAt: "2026-09-09T00:00:00Z", lastMessageAt: "2026-09-09T00:00:00Z" }, 201),
    );

    const result = await client(fetchMock).createConversation(7, "왜 PG?", signal());

    expect(result).toEqual({ sessionId: 12 });
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("http://localhost:8080/api/v1/workspaces/7/conversations");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body as string)).toEqual({ title: "왜 PG?" });
  });

  it("턴 저장을 POST하고 userMessageId·messageId를 돌려준다(S2 계약)", async () => {
    const fetchMock = vi.fn(async () => json({ userMessageId: 41, messageId: 42 }, 201));
    const turn = {
      question: "왜 PG?",
      answer: "pgvector 때문",
      references: [{ importRunId: 301, importedPageId: 201, chunkIndex: 2, score: 0.9 }],
    };

    const result = await client(fetchMock).saveTurn(12, turn, signal());

    expect(result).toEqual({ userMessageId: 41, messageId: 42 });
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("http://localhost:8080/api/v1/conversations/12/turns");
    expect(init.method).toBe("POST");
    expect(init.headers).toMatchObject({ Authorization: "Bearer jwt", "Content-Type": "application/json" });
    expect(JSON.parse(init.body as string)).toEqual(turn);
  });

  it("세션 생성·턴 저장 응답 모양이 다르면 KNOT_API_MALFORMED", async () => {
    await expect(client(vi.fn(async () => json({ title: "x" }, 201))).createConversation(7, "t", signal())).rejects.toMatchObject(
      KNOT_API_ERRORS.malformed,
    );
    await expect(
      client(vi.fn(async () => json({ messageId: "42" }, 201))).saveTurn(12, { question: "q", answer: "a", references: [] }, signal()),
    ).rejects.toMatchObject(KNOT_API_ERRORS.malformed);
  });

  it("READY가 아닌 응답은 status·fallbackAnswer만 돌려준다", async () => {
    const fetchMock = vi.fn(async () => json({ status: "NEEDS_CLARIFICATION", fallbackAnswer: "범위가 넓어요" }));

    expect(await client(fetchMock).searchWorkspace(7, "질문", signal())).toEqual({
      status: "NEEDS_CLARIFICATION",
      fallbackAnswer: "범위가 넓어요",
    });
  });

  it("서버 HTTP 오류는 본문의 code·message를 그대로 KnotApiError로 옮긴다", async () => {
    const fetchMock = vi.fn(async () => json({ code: "WORKSPACE_ACCESS_DENIED", message: "워크스페이스에 접근할 수 없습니다" }, 403));

    await expect(client(fetchMock).searchWorkspace(7, "질문", signal())).rejects.toMatchObject({
      status: 403,
      code: "WORKSPACE_ACCESS_DENIED",
      message: "워크스페이스에 접근할 수 없습니다",
    });
  });

  it("본문을 읽지 못한 HTTP 오류는 UNKNOWN", async () => {
    const fetchMock = vi.fn(async () => new Response("", { status: 502 }));

    await expect(client(fetchMock).listWorkspaces(signal())).rejects.toMatchObject({ status: 502, code: "UNKNOWN" });
  });

  it("저장된 토큰이 없으면 요청 없이 UNAUTHENTICATED('Knot 앱에 로그인하세요')", async () => {
    const fetchMock = vi.fn();

    await expect(client(fetchMock, null).listWorkspaces(signal())).rejects.toMatchObject(KNOT_API_ERRORS.unauthenticated);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("네트워크 오류·타임아웃은 KNOT_API_UNREACHABLE, 호출자 취소는 AbortError", async () => {
    const networkError = vi.fn(async () => {
      throw new TypeError("fetch failed");
    });
    await expect(client(networkError).listWorkspaces(signal())).rejects.toMatchObject(KNOT_API_ERRORS.unreachable);

    const hanging = vi.fn(
      (_url: string, init?: RequestInit) =>
        new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener("abort", () => reject(init.signal?.reason));
        }),
    ) as unknown as typeof fetch;
    await expect(client(hanging, "jwt", 10).listWorkspaces(signal())).rejects.toMatchObject(KNOT_API_ERRORS.unreachable);

    const controller = new AbortController();
    const promise = client(hanging).listWorkspaces(controller.signal);
    controller.abort();
    await expect(promise).rejects.toMatchObject({ name: "AbortError" });
  });

  it("응답 모양이 계약과 다르면 KNOT_API_MALFORMED", async () => {
    await expect(client(vi.fn(async () => json({ workspaces: "x" }))).listWorkspaces(signal())).rejects.toMatchObject(
      KNOT_API_ERRORS.malformed,
    );
    await expect(
      client(vi.fn(async () => json({ status: "READY", groundingRules: "r", chunks: [{ importRunId: "x" }] }))).searchWorkspace(1, "q", signal()),
    ).rejects.toMatchObject(KNOT_API_ERRORS.malformed);
    await expect(client(vi.fn(async () => new Response("not json"))).listWorkspaces(signal())).rejects.toMatchObject(
      KNOT_API_ERRORS.malformed,
    );
  });

  it("로그에 토큰·질문·답변·본문을 남기지 않는다", async () => {
    logMock.warn.mockClear();
    const fetchMock = vi.fn(async () => json({ code: "X", message: "y" }, 500));

    await client(fetchMock, "secret-jwt").searchWorkspace(7, "비밀 질문", signal()).catch(() => undefined);
    await client(fetchMock, "secret-jwt")
      .saveTurn(12, { question: "비밀 질문", answer: "비밀 답변", references: [] }, signal())
      .catch(() => undefined);

    expect(JSON.stringify(logMock.warn.mock.calls)).not.toMatch(/secret-jwt|비밀 질문|비밀 답변/);
  });
});
