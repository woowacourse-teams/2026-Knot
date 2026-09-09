/**
 * 로컬 MCP 서버 (기획서 6.4, 로드맵 Q47·Q48·Q49).
 *
 * 공식 TypeScript SDK의 Streamable HTTP 서버 전송으로 `http://127.0.0.1:<port>/mcp` 단일 엔드포인트를
 * 연다. 요청마다 `guard`(Origin·Host·연결 토큰)를 통과한 뒤 무상태(`sessionIdGenerator: undefined`)
 * 전송 + `McpServer`를 새로 만들어 처리한다 — SDK의 무상태 예시와 같은 방식이며, initialize 세션을 쓰는
 * 구 개정판 클라이언트도 세션 ID 없이 그대로 동작한다(로드맵 U28).
 *
 * 도구 실행은 하지 않는다. `requestTool`로 main에 넘기고 결과만 MCP 규약 모양으로 옮긴다.
 * 이 프로세스는 서버 액세스 토큰을 모르고 LLM을 부르지 않는다(불변 계약 2·3번).
 * Electron에 의존하지 않으므로 vitest에서 실제 HTTP로 검증한다.
 */

import { createServer } from "node:http";
import type { IncomingMessage, Server, ServerResponse } from "node:http";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import type { CallToolResult } from "@modelcontextprotocol/sdk/types.js";
import { z } from "zod";
import { MAX_ANSWER_LENGTH, MAX_ANSWER_SOURCES, MAX_QUERY_LENGTH } from "../shared/agentProtocol";
import type { AgentToolName, AgentToolOutcome } from "../shared/agentProtocol";
import { checkMcpRequest } from "./guard";

export type ToolRequester = (tool: AgentToolName, input: unknown) => Promise<AgentToolOutcome>;

export interface KnotMcpServerOptions {
  version: string;
  /** 스킬 본문 요약. 클라이언트가 모델에 노출하는지는 CLI마다 다르다(로드맵 U30) */
  instructions: string;
}

export interface McpHttpServerOptions extends KnotMcpServerOptions {
  port: number;
  token: string;
  requestTool: ToolRequester;
  onError?: (error: unknown) => void;
}

export interface McpHttpServerHandle {
  port: number;
  close(): Promise<void>;
}

const SEARCH_INPUT_SCHEMA = {
  query: z
    .string()
    .min(1)
    .max(MAX_QUERY_LENGTH)
    .describe("검색할 질문. 사용자의 질문을 그대로 넣는다(1~10,000자)"),
  workspaceId: z
    .number()
    .int()
    .positive()
    .optional()
    .describe("검색할 Knot 워크스페이스 ID. 하나뿐이면 생략 가능. 여럿이면 list_workspaces로 고른다"),
};

/** `show_answer` 입력(로드맵 Q51). `sources`는 `search_documents`가 돌려준 청크의 식별 필드만 */
const SHOW_ANSWER_INPUT_SCHEMA = {
  workspaceId: z.number().int().positive().describe("답변을 저장할 Knot 워크스페이스 ID(search_documents에 쓴 값)"),
  question: z.string().min(1).max(MAX_QUERY_LENGTH).describe("사용자의 질문 원문(1~10,000자)"),
  answer: z.string().min(1).max(MAX_ANSWER_LENGTH).describe("터미널에 표시한 답변 전문(마크다운 그대로)"),
  sources: z
    .array(
      z.object({
        importRunId: z.number().int().positive(),
        importedPageId: z.number().int().positive(),
        chunkIndex: z.number().int().nonnegative(),
        score: z.number(),
      }),
    )
    .max(MAX_ANSWER_SOURCES)
    .describe(
      "답에 실제로 사용한 근거. search_documents 결과 structuredContent.chunks의 importRunId·importedPageId·chunkIndex·score만 그대로 넣는다(최대 8개, 순서가 rank)",
    ),
  sessionId: z
    .number()
    .int()
    .positive()
    .optional()
    .describe("같은 터미널 대화에서 앞선 show_answer가 돌려준 sessionId. 넘기면 그 대화에 이어서 저장한다"),
};

/** 도구 정의는 스킬 본문(`resources/skills/knot/SKILL.md`)과 같은 문구를 쓴다(기획서 6.4) */
export function createKnotMcpServer(requestTool: ToolRequester, options: KnotMcpServerOptions): McpServer {
  const server = new McpServer(
    { name: "knot", version: options.version },
    { instructions: options.instructions },
  );

  server.registerTool(
    "list_workspaces",
    {
      title: "Knot 워크스페이스 목록",
      description:
        "로그인한 사용자가 속한 Knot 워크스페이스 목록을 돌려준다. search_documents에 넘길 workspaceId를 고를 때 쓴다.",
      annotations: { readOnlyHint: true, openWorldHint: false },
    },
    async () => toCallToolResult(await requestTool("list_workspaces", {})),
  );

  server.registerTool(
    "search_documents",
    {
      title: "Knot 팀 문서 검색",
      description:
        "Knot 워크스페이스에 동기화된 팀 문서(회의록·결정 기록·기획서)에서 질문과 관련된 근거 청크를 최대 8개 찾는다. " +
        "결과 앞머리의 근거 규칙 문장을 지켜 답하고, 출처는 결과의 `문서 링크`로 표시한다. 아무것도 저장하지 않으므로 " +
        "한 질문에 여러 번 검색해도 된다.",
      inputSchema: SEARCH_INPUT_SCHEMA,
      annotations: { readOnlyHint: true, openWorldHint: false },
    },
    async (input) => toCallToolResult(await requestTool("search_documents", input)),
  );

  server.registerTool(
    "show_answer",
    {
      title: "Knot 앱에 답변 표시",
      description:
        "터미널에 표시한 답변과 그 근거를 Knot 앱의 대화에 저장하고, 앱 창을 앞으로 가져와 그 대화를 연다. " +
        "사용자가 답변을 Knot 앱에서 보거나 팀 기록으로 남기고 싶다고 했을 때만 부른다. " +
        "sources에는 search_documents 결과 structuredContent.chunks의 값만 넣는다. 돌려받은 sessionId를 " +
        "같은 대화의 다음 show_answer에 넘기면 한 대화로 이어진다.",
      inputSchema: SHOW_ANSWER_INPUT_SCHEMA,
      annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: false, openWorldHint: false },
    },
    async (input) => toCallToolResult(await requestTool("show_answer", input)),
  );

  return server;
}

/** main의 답을 MCP 도구 결과로 옮긴다. 오류는 `isError: true` + `코드: 문구` 텍스트다(로드맵 Q49) */
export function toCallToolResult(outcome: AgentToolOutcome): CallToolResult {
  if ("error" in outcome) {
    return {
      content: [{ type: "text", text: `${outcome.error.code}: ${outcome.error.message}` }],
      isError: true,
    };
  }
  const { text, structuredContent, isError } = outcome.result;
  return {
    content: [{ type: "text", text }],
    ...(structuredContent === undefined ? {} : { structuredContent }),
    ...(isError === true ? { isError: true } : {}),
  };
}

export function startMcpHttpServer(options: McpHttpServerOptions): Promise<McpHttpServerHandle> {
  const { port, token, requestTool, onError } = options;

  const httpServer: Server = createServer((request, response) => {
    void handle(request, response).catch((error: unknown) => {
      onError?.(error);
      if (!response.headersSent) {
        response.writeHead(500, { "content-type": "application/json" });
      }
      if (!response.writableEnded) {
        response.end(JSON.stringify({ error: "Internal error" }));
      }
    });
  });

  async function handle(request: IncomingMessage, response: ServerResponse): Promise<void> {
    const verdict = checkMcpRequest(request, { port, token });
    if (!verdict.ok) {
      response.writeHead(verdict.status, { "content-type": "application/json" });
      response.end(JSON.stringify({ error: verdict.message }));
      return;
    }

    const server = createKnotMcpServer(requestTool, options);
    const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });
    response.on("close", () => {
      void transport.close();
      void server.close();
    });
    await server.connect(transport);
    await transport.handleRequest(request, response);
  }

  return new Promise<McpHttpServerHandle>((resolve, reject) => {
    httpServer.once("error", reject);
    // 127.0.0.1에만 바인딩한다(로드맵 Q47, MCP 스펙 SHOULD). 0.0.0.0로 바꾸지 않는다
    httpServer.listen(port, "127.0.0.1", () => {
      httpServer.off("error", reject);
      httpServer.on("error", (error) => onError?.(error));
      resolve({
        port,
        close: () =>
          new Promise<void>((done) => {
            httpServer.closeAllConnections();
            httpServer.close(() => done());
          }),
      });
    });
  });
}
