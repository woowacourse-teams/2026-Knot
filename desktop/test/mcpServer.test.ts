import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { createServer as createNetServer } from "node:net";
import { request as httpRequest } from "node:http";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import { startMcpHttpServer, toCallToolResult } from "../src/mcp/server";
import type { McpHttpServerHandle, ToolRequester } from "../src/mcp/server";

const TOKEN = "dGVzdC1jb25uZWN0aW9uLXRva2VuLTAxMjM0NTY3ODk";

async function freePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const probe = createNetServer();
    probe.once("error", reject);
    probe.listen(0, "127.0.0.1", () => {
      const address = probe.address();
      const port = typeof address === "object" && address !== null ? address.port : 0;
      probe.close(() => resolve(port));
    });
  });
}

function rawRequest(options: {
  port: number;
  path?: string;
  method?: string;
  headers?: Record<string, string>;
  body?: string;
}): Promise<{ status: number; body: string }> {
  return new Promise((resolve, reject) => {
    const req = httpRequest(
      {
        host: "127.0.0.1",
        port: options.port,
        path: options.path ?? "/mcp",
        method: options.method ?? "POST",
        headers: {
          "content-type": "application/json",
          accept: "application/json, text/event-stream",
          ...options.headers,
        },
      },
      (res) => {
        let body = "";
        res.setEncoding("utf8");
        res.on("data", (chunk: string) => {
          body += chunk;
        });
        res.on("end", () => resolve({ status: res.statusCode ?? 0, body }));
      },
    );
    req.on("error", reject);
    req.end(options.body ?? '{"jsonrpc":"2.0","id":1,"method":"ping"}');
  });
}

describe("로컬 MCP 서버 (Streamable HTTP, 로드맵 Q47·Q48·Q49)", () => {
  let port = 0;
  let handle: McpHttpServerHandle;
  const requestTool = vi.fn<ToolRequester>();

  beforeAll(async () => {
    port = await freePort();
    handle = await startMcpHttpServer({
      port,
      token: TOKEN,
      version: "0.1.0-test",
      instructions: "테스트 안내",
      requestTool,
    });
  });

  afterAll(async () => {
    await handle.close();
  });

  async function connect(headers: Record<string, string> = { Authorization: `Bearer ${TOKEN}` }): Promise<Client> {
    const client = new Client({ name: "vitest", version: "0.0.0" });
    const transport = new StreamableHTTPClientTransport(new URL(`http://127.0.0.1:${port}/mcp`), {
      requestInit: { headers },
    });
    await client.connect(transport);
    return client;
  }

  it("공식 SDK 클라이언트가 Bearer 토큰으로 붙어 도구 세 개와 instructions를 본다", async () => {
    const client = await connect();

    const { tools } = await client.listTools();

    expect(tools.map((tool) => tool.name).sort()).toEqual(["list_workspaces", "search_documents", "show_answer"]);
    const search = tools.find((tool) => tool.name === "search_documents");
    expect(search?.inputSchema).toMatchObject({ type: "object", required: ["query"] });
    const show = tools.find((tool) => tool.name === "show_answer");
    expect(show?.inputSchema).toMatchObject({ type: "object", required: ["workspaceId", "question", "answer", "sources"] });
    expect(show?.annotations).toMatchObject({ readOnlyHint: false, destructiveHint: false });
    expect(client.getInstructions()).toBe("테스트 안내");
    await client.close();
  });

  it("show_answer 호출이 main으로 위임되고 structuredContent가 돌아온다(S10)", async () => {
    requestTool.mockResolvedValueOnce({
      result: { text: "저장했어요", structuredContent: { workspaceId: 7, sessionId: 12, messageId: 42, userMessageId: 41 } },
    });
    const client = await connect();
    const input = {
      workspaceId: 7,
      question: "왜 PG?",
      answer: "pgvector 때문",
      sources: [{ importRunId: 301, importedPageId: 201, chunkIndex: 2, score: 0.9 }],
    };

    const result = await client.callTool({ name: "show_answer", arguments: input });

    expect(requestTool).toHaveBeenCalledWith("show_answer", input);
    expect(result).toMatchObject({
      content: [{ type: "text", text: "저장했어요" }],
      structuredContent: { sessionId: 12, messageId: 42 },
    });
    expect(result.isError).toBeFalsy();
    await client.close();
  });

  it("show_answer의 sources가 9개면 SDK 입력 검증에서 거절돼 main까지 가지 않는다", async () => {
    requestTool.mockClear();
    const client = await connect();
    const source = { importRunId: 301, importedPageId: 201, chunkIndex: 2, score: 0.9 };

    const result = await client.callTool({
      name: "show_answer",
      arguments: { workspaceId: 7, question: "q", answer: "a", sources: Array.from({ length: 9 }, () => source) },
    });

    expect(result).toMatchObject({ isError: true, content: [{ type: "text", text: expect.stringContaining("Input validation error") }] });
    expect(requestTool).not.toHaveBeenCalled();
    await client.close();
  });

  it("search_documents 호출이 main으로 위임되고 결과가 content·structuredContent로 돌아온다", async () => {
    requestTool.mockResolvedValueOnce({
      result: { text: "규칙\n\n[근거 문서 1]", structuredContent: { status: "READY", chunks: [] } },
    });
    const client = await connect();

    const result = await client.callTool({ name: "search_documents", arguments: { query: "왜 PG?", workspaceId: 3 } });

    expect(requestTool).toHaveBeenCalledWith("search_documents", { query: "왜 PG?", workspaceId: 3 });
    expect(result).toMatchObject({
      content: [{ type: "text", text: "규칙\n\n[근거 문서 1]" }],
      structuredContent: { status: "READY", chunks: [] },
    });
    expect(result.isError).toBeFalsy();
    await client.close();
  });

  it("list_workspaces 호출은 입력 없이 위임된다", async () => {
    requestTool.mockResolvedValueOnce({ result: { text: "워크스페이스 1개", structuredContent: { workspaces: [] } } });
    const client = await connect();

    const result = await client.callTool({ name: "list_workspaces", arguments: {} });

    expect(requestTool).toHaveBeenCalledWith("list_workspaces", {});
    expect(result.content).toEqual([{ type: "text", text: "워크스페이스 1개" }]);
    await client.close();
  });

  it("main의 error는 isError + '코드: 문구' 텍스트로 돌아온다", async () => {
    requestTool.mockResolvedValueOnce({ error: { code: "UNAUTHENTICATED", message: "Knot 앱에 로그인하세요" } });
    const client = await connect();

    const result = await client.callTool({ name: "list_workspaces", arguments: {} });

    expect(result).toMatchObject({ isError: true, content: [{ type: "text", text: "UNAUTHENTICATED: Knot 앱에 로그인하세요" }] });
    await client.close();
  });

  it("빈 query는 SDK 입력 검증에서 거절돼 main까지 가지 않는다", async () => {
    requestTool.mockClear();
    const client = await connect();

    const result = await client.callTool({ name: "search_documents", arguments: { query: "" } });

    expect(result).toMatchObject({ isError: true, content: [{ type: "text", text: expect.stringContaining("Input validation error") }] });
    expect(requestTool).not.toHaveBeenCalled();
    await client.close();
  });

  it("연결 토큰이 없거나 다르면 401", async () => {
    expect((await rawRequest({ port })).status).toBe(401);
    expect((await rawRequest({ port, headers: { authorization: "Bearer wrong" } })).status).toBe(401);
    await expect(connect({})).rejects.toThrow();
  });

  it("Origin 헤더가 있으면 403", async () => {
    const response = await rawRequest({
      port,
      headers: { authorization: `Bearer ${TOKEN}`, origin: `http://127.0.0.1:${port}` },
    });

    expect(response.status).toBe(403);
  });

  it("Host가 127.0.0.1·localhost가 아니면 403", async () => {
    const response = await rawRequest({
      port,
      headers: { authorization: `Bearer ${TOKEN}`, host: `knot.evil.test:${port}` },
    });

    expect(response.status).toBe(403);
  });

  it("/mcp 밖의 경로는 404", async () => {
    const response = await rawRequest({ port, path: "/", headers: { authorization: `Bearer ${TOKEN}` } });

    expect(response.status).toBe(404);
  });

  it("toCallToolResult는 structuredContent·isError를 있을 때만 싣는다", () => {
    expect(toCallToolResult({ result: { text: "t" } })).toEqual({ content: [{ type: "text", text: "t" }] });
    expect(toCallToolResult({ result: { text: "t", isError: true, structuredContent: { a: 1 } } })).toEqual({
      content: [{ type: "text", text: "t" }],
      structuredContent: { a: 1 },
      isError: true,
    });
    expect(toCallToolResult({ error: { code: "X", message: "y" } })).toEqual({
      content: [{ type: "text", text: "X: y" }],
      isError: true,
    });
  });
});
