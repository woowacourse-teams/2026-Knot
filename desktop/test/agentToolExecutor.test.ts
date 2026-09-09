import { describe, expect, it, vi } from "vitest";
import { logMock } from "./helpers/logMock";

vi.mock("electron-log/main", () => ({ default: logMock }));

const { KnotApiError } = await import("../src/main/chat/knotApi");
const { TOOL_ERRORS, createToolExecutor, formatSearchResult, groundingBlock, parseSearchInput, parseShowAnswerInput, sessionTitleFrom } =
  await import("../src/main/agent/toolExecutor");
const { isAgentToolName } = await import("../src/shared/agentProtocol");
type KnotApiClient = import("../src/main/chat/knotApi").KnotApiClient;
type WorkspaceSearchResponse = import("../src/main/chat/knotApi").WorkspaceSearchResponse;

const CHUNKS = [
  { importRunId: 301, importedPageId: 201, chunkIndex: 2, title: "DB 회의록", sourceUrl: "https://n.so/1", content: "pgvector 때문", score: 0.9 },
  { importRunId: 301, importedPageId: 202, chunkIndex: 0, title: "ADR", sourceUrl: "https://n.so/2", content: "결정 근거", score: 0.7 },
];
const READY: WorkspaceSearchResponse = { status: "READY", groundingRules: "규칙\n\n", chunks: CHUNKS };

function fakeApi(overrides: Partial<KnotApiClient> = {}): KnotApiClient {
  return {
    listWorkspaces: vi.fn(async () => [{ id: 1, name: "팀" }]),
    searchWorkspace: vi.fn(async () => READY),
    createConversation: vi.fn(async () => ({ sessionId: 12 })),
    saveTurn: vi.fn(async () => ({ userMessageId: 41, messageId: 42 })),
    listMessages: vi.fn(async () => []),
    ...overrides,
  };
}

const SOURCES = [
  { importRunId: 301, importedPageId: 201, chunkIndex: 2, score: 0.9 },
  { importRunId: 301, importedPageId: 202, chunkIndex: 0, score: 0.7 },
];
const SHOW = { workspaceId: 7, question: "왜 PG?", answer: "pgvector 때문이에요.", sources: SOURCES };

const signal = () => new AbortController().signal;

describe("list_workspaces", () => {
  it("목록 텍스트와 structuredContent.workspaces를 돌려준다", async () => {
    const api = fakeApi({ listWorkspaces: vi.fn(async () => [{ id: 1, name: "팀" }, { id: 2, name: "개인", role: "OWNER" }]) });

    const execution = await createToolExecutor(api).execute("list_workspaces", {}, signal());

    expect(execution.workspaceId).toBeNull();
    expect(execution.outcome).toEqual({
      result: {
        text: "워크스페이스 2개:\n- [1] 팀\n- [2] 개인 (OWNER)",
        structuredContent: { workspaces: [{ id: 1, name: "팀" }, { id: 2, name: "개인", role: "OWNER" }] },
      },
    });
  });

  it("워크스페이스가 없으면 안내 문구를 돌려준다(오류 아님)", async () => {
    const api = fakeApi({ listWorkspaces: vi.fn(async () => []) });

    const execution = await createToolExecutor(api).execute("list_workspaces", {}, signal());

    expect(execution.outcome).toMatchObject({ result: { text: TOOL_ERRORS.noWorkspace.message } });
  });
});

describe("search_documents", () => {
  it("workspaceId가 있으면 그 워크스페이스를 검색하고 규칙 문장 + 근거 블록을 조립한다", async () => {
    const api = fakeApi();

    const execution = await createToolExecutor(api).execute("search_documents", { query: "왜 PG?", workspaceId: 7 }, signal());

    expect(api.searchWorkspace).toHaveBeenCalledWith(7, "왜 PG?", expect.any(AbortSignal));
    expect(api.listWorkspaces).not.toHaveBeenCalled();
    expect(execution.workspaceId).toBe(7);
    if (!("result" in execution.outcome)) throw new Error("result expected");
    const { text, structuredContent, isError } = execution.outcome.result;
    expect(isError).toBeUndefined();
    expect(text.startsWith("규칙\n\n[근거 문서 1]\n")).toBe(true);
    expect(text).toContain("제목: DB 회의록\n문서 ID: 201\n문서 링크: https://n.so/1\n청크: 2\n내용:\npgvector 때문\n\n");
    expect(text).toContain("[근거 문서 2]\n제목: ADR");
    expect(structuredContent).toEqual({ status: "READY", chunks: CHUNKS });
  });

  it("workspaceId가 없고 워크스페이스가 하나면 그것을 쓴다", async () => {
    const api = fakeApi();

    const execution = await createToolExecutor(api).execute("search_documents", { query: "q" }, signal());

    expect(api.searchWorkspace).toHaveBeenCalledWith(1, "q", expect.any(AbortSignal));
    expect(execution.workspaceId).toBe(1);
  });

  it("workspaceId가 없고 워크스페이스가 여럿이면 isError로 목록을 돌려주고 검색하지 않는다", async () => {
    const api = fakeApi({ listWorkspaces: vi.fn(async () => [{ id: 1, name: "팀" }, { id: 2, name: "개인" }]) });

    const execution = await createToolExecutor(api).execute("search_documents", { query: "q" }, signal());

    expect(api.searchWorkspace).not.toHaveBeenCalled();
    expect(execution.outcome).toMatchObject({
      result: {
        isError: true,
        text: expect.stringContaining("list_workspaces"),
        structuredContent: { workspaces: [{ id: 1, name: "팀" }, { id: 2, name: "개인" }] },
      },
    });
    if (!("result" in execution.outcome)) throw new Error("result expected");
    expect(execution.outcome.result.text).toContain("- [2] 개인");
  });

  it("workspaceId가 없고 워크스페이스도 없으면 NO_WORKSPACE 오류", async () => {
    const api = fakeApi({ listWorkspaces: vi.fn(async () => []) });

    const execution = await createToolExecutor(api).execute("search_documents", { query: "q" }, signal());

    expect(execution.outcome).toEqual({ error: TOOL_ERRORS.noWorkspace });
  });

  it("READY가 아니면 fallbackAnswer만 텍스트로 돌려준다", async () => {
    const api = fakeApi({ searchWorkspace: vi.fn(async () => ({ status: "NO_RESULT" as const, fallbackAnswer: "못 찾음" })) });

    const execution = await createToolExecutor(api).execute("search_documents", { query: "q", workspaceId: 1 }, signal());

    expect(execution.outcome).toEqual({
      result: { text: "못 찾음", structuredContent: { status: "NO_RESULT", fallbackAnswer: "못 찾음", chunks: [] } },
    });
  });

  it("서버 HTTP 오류는 code·message를 그대로 error로 옮긴다", async () => {
    const api = fakeApi({
      searchWorkspace: vi.fn(async () => {
        throw new KnotApiError(409, "CHAT_DOCUMENTS_NOT_READY", "문서 동기화가 완료된 후 검색할 수 있습니다");
      }),
    });

    const execution = await createToolExecutor(api).execute("search_documents", { query: "q", workspaceId: 1 }, signal());

    expect(execution.outcome).toEqual({
      error: { code: "CHAT_DOCUMENTS_NOT_READY", message: "문서 동기화가 완료된 후 검색할 수 있습니다" },
    });
  });

  it("저장된 토큰이 없으면 UNAUTHENTICATED('Knot 앱에 로그인하세요')", async () => {
    const api = fakeApi({
      listWorkspaces: vi.fn(async () => {
        throw new KnotApiError(401, "UNAUTHENTICATED", "Knot 앱에 로그인하세요");
      }),
    });

    const execution = await createToolExecutor(api).execute("list_workspaces", {}, signal());

    expect(execution.outcome).toEqual({ error: { code: "UNAUTHENTICATED", message: "Knot 앱에 로그인하세요" } });
  });

  it("입력이 계약과 다르면 서버를 부르지 않고 INVALID_TOOL_INPUT", async () => {
    const api = fakeApi();
    const executor = createToolExecutor(api);

    for (const input of [null, {}, { query: "" }, { query: "   " }, { query: "q", workspaceId: -1 }, { query: "q", workspaceId: "1" }, { query: "x".repeat(10_001) }]) {
      const execution = await executor.execute("search_documents", input, signal());
      expect(execution.outcome, JSON.stringify(input)?.slice(0, 40)).toMatchObject({ error: { code: TOOL_ERRORS.invalidInput } });
    }
    expect(api.listWorkspaces).not.toHaveBeenCalled();
    expect(api.searchWorkspace).not.toHaveBeenCalled();
  });

  it("parseSearchInput은 workspaceId 생략·null을 같은 뜻으로 본다", () => {
    expect(parseSearchInput({ query: "q" })).toEqual({ query: "q", workspaceId: null });
    expect(parseSearchInput({ query: "q", workspaceId: null })).toEqual({ query: "q", workspaceId: null });
    expect(parseSearchInput({ query: "q", workspaceId: 3 })).toEqual({ query: "q", workspaceId: 3 });
  });

  it("groundingBlock·formatSearchResult는 서버 groundingPrompt와 같은 순서·형식이다", () => {
    expect(groundingBlock(0, CHUNKS[0]!)).toBe(
      "[근거 문서 1]\n제목: DB 회의록\n문서 ID: 201\n문서 링크: https://n.so/1\n청크: 2\n내용:\npgvector 때문\n\n",
    );
    expect(formatSearchResult(READY).text).toBe("규칙\n\n" + groundingBlock(0, CHUNKS[0]!) + groundingBlock(1, CHUNKS[1]!));
  });
});

describe("show_answer (로드맵 Q51)", () => {
  it("sessionId가 없으면 질문 앞 50자를 제목으로 세션을 만들고 턴을 저장한 뒤 창을 그 세션으로 보낸다", async () => {
    const api = fakeApi();
    const presentAnswer = vi.fn();

    const execution = await createToolExecutor(api, { presentAnswer }).execute("show_answer", SHOW, signal());

    expect(api.createConversation).toHaveBeenCalledWith(7, "왜 PG?", expect.any(AbortSignal));
    expect(api.saveTurn).toHaveBeenCalledWith(
      12,
      { question: "왜 PG?", answer: "pgvector 때문이에요.", references: SOURCES },
      expect.any(AbortSignal),
    );
    expect(presentAnswer).toHaveBeenCalledWith({ type: "chat", workspaceId: "7", sessionId: "12" });
    expect(execution.workspaceId).toBe(7);
    expect(execution.outcome).toEqual({
      result: {
        text: expect.stringContaining("sessionId 12"),
        structuredContent: { workspaceId: 7, sessionId: 12, messageId: 42, userMessageId: 41 },
      },
    });
  });

  it("sessionId가 있으면 세션을 만들지 않고 그 세션에 이어서 저장한다", async () => {
    const api = fakeApi();
    const presentAnswer = vi.fn();

    await createToolExecutor(api, { presentAnswer }).execute("show_answer", { ...SHOW, sessionId: 5 }, signal());

    expect(api.createConversation).not.toHaveBeenCalled();
    expect(api.saveTurn).toHaveBeenCalledWith(5, expect.anything(), expect.any(AbortSignal));
    expect(presentAnswer).toHaveBeenCalledWith({ type: "chat", workspaceId: "7", sessionId: "5" });
  });

  it("presentAnswer가 없으면 저장만 한다(헤드리스)", async () => {
    const api = fakeApi();

    const execution = await createToolExecutor(api).execute("show_answer", SHOW, signal());

    expect(execution.outcome).toMatchObject({ result: { structuredContent: { sessionId: 12 } } });
  });

  it("저장이 실패하면 서버 code·message를 그대로 error로 옮기고 창을 건드리지 않는다", async () => {
    const api = fakeApi({
      saveTurn: vi.fn(async () => {
        throw new KnotApiError(409, "CHAT_TURN_IN_PROGRESS", "이전 질문의 답변이 진행 중입니다");
      }),
    });
    const presentAnswer = vi.fn();

    const execution = await createToolExecutor(api, { presentAnswer }).execute("show_answer", SHOW, signal());

    expect(execution.workspaceId).toBe(7);
    expect(execution.outcome).toEqual({ error: { code: "CHAT_TURN_IN_PROGRESS", message: "이전 질문의 답변이 진행 중입니다" } });
    expect(presentAnswer).not.toHaveBeenCalled();
  });

  it("세션 생성이 실패하면 턴을 저장하지 않는다", async () => {
    const api = fakeApi({
      createConversation: vi.fn(async () => {
        throw new KnotApiError(403, "WORKSPACE_ACCESS_DENIED", "워크스페이스에 접근할 수 없습니다");
      }),
    });

    const execution = await createToolExecutor(api).execute("show_answer", SHOW, signal());

    expect(api.saveTurn).not.toHaveBeenCalled();
    expect(execution.outcome).toEqual({ error: { code: "WORKSPACE_ACCESS_DENIED", message: "워크스페이스에 접근할 수 없습니다" } });
  });

  it("입력이 계약과 다르면 서버를 부르지 않고 INVALID_TOOL_INPUT", async () => {
    const api = fakeApi();
    const executor = createToolExecutor(api);
    const nine = Array.from({ length: 9 }, () => SOURCES[0]!);

    for (const input of [
      null,
      { ...SHOW, workspaceId: 0 },
      { ...SHOW, question: "" },
      { ...SHOW, question: "x".repeat(10_001) },
      { ...SHOW, answer: "   " },
      { ...SHOW, answer: "x".repeat(100_001) },
      { ...SHOW, sources: "no" },
      { ...SHOW, sources: nine },
      { ...SHOW, sources: [{ importRunId: 1, importedPageId: 1, chunkIndex: -1, score: 0.5 }] },
      { ...SHOW, sources: [{ importRunId: 1, importedPageId: 1, chunkIndex: 0, score: "0.5" }] },
      { ...SHOW, sessionId: 1.5 },
    ]) {
      const execution = await executor.execute("show_answer", input, signal());
      expect(execution.outcome, JSON.stringify(input)?.slice(0, 60)).toMatchObject({ error: { code: TOOL_ERRORS.invalidInput } });
    }
    expect(api.createConversation).not.toHaveBeenCalled();
    expect(api.saveTurn).not.toHaveBeenCalled();
  });

  it("오류 메시지에 질문·답변 본문을 넣지 않는다", async () => {
    const execution = await createToolExecutor(fakeApi()).execute(
      "show_answer",
      { ...SHOW, question: "비밀 질문", answer: "비밀 답변", sessionId: -1 },
      signal(),
    );

    expect(JSON.stringify(execution.outcome)).not.toMatch(/비밀 질문|비밀 답변/);
  });

  it("parseShowAnswerInput은 sessionId 생략·null을 같은 뜻으로 보고 sources를 순서대로 옮긴다", () => {
    expect(parseShowAnswerInput(SHOW)).toEqual({ ...SHOW, sessionId: null });
    expect(parseShowAnswerInput({ ...SHOW, sessionId: null })).toEqual({ ...SHOW, sessionId: null });
    expect(parseShowAnswerInput({ ...SHOW, sources: [] })).toEqual({ ...SHOW, sources: [], sessionId: null });
  });

  it("sessionTitleFrom은 공백을 접고 앞 50자만 쓴다", () => {
    expect(sessionTitleFrom("  왜\n  PG를   썼나요?  ")).toBe("왜 PG를 썼나요?");
    expect(sessionTitleFrom("가".repeat(80))).toBe("가".repeat(50));
  });

  it("도구 이름 집합에 show_answer가 있다", () => {
    expect(isAgentToolName("show_answer")).toBe(true);
    expect(isAgentToolName("delete_everything")).toBe(false);
  });
});
