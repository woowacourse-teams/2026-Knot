/**
 * MCP 서버 프로세스(`src/mcp`) ↔ main(`src/main/agent`) 사이의 `MessagePort` 계약 (기획서 4.4·6.4).
 *
 * MCP 프로세스는 도구 호출을 `{requestId, tool, input}`으로 main에 넘기고, main이 서버 API를 부른 뒤
 * `{requestId, result}` 또는 `{requestId, error: {code, message}}`로 답한다. 메시지에는 서버 액세스
 * 토큰·연결 토큰이 들어가지 않는다(불변 계약 2번). MCP 프로세스는 서버 액세스 토큰을 모른다(로드맵 Q47).
 */

export const AGENT_TOOL_NAMES = ["list_workspaces", "search_documents", "show_answer"] as const;
export type AgentToolName = (typeof AGENT_TOOL_NAMES)[number];

/** `search_documents.query`·`show_answer.question` 상한. 서버 검색·턴 저장 API의 상한과 같다(로드맵 Q49·Q51) */
export const MAX_QUERY_LENGTH = 10_000;
/** `show_answer.answer` 상한. 계약에 없는 방어값이다(로드맵 Q51 착수 가정) */
export const MAX_ANSWER_LENGTH = 100_000;
/** `show_answer.sources` 상한. 턴 저장 API의 `references` ≤ 8(기획서 6.4 `S2`) */
export const MAX_ANSWER_SOURCES = 8;
/** `show_answer`가 세션을 새로 만들 때 제목으로 쓰는 질문 앞 글자 수(로드맵 Q51) */
export const SESSION_TITLE_LENGTH = 50;
/** 동시 도구 호출 상한. 넘으면 `AGENT_TOO_MANY_REQUESTS`(로드맵 Q49) */
export const MAX_CONCURRENT_TOOL_CALLS = 4;
/** MCP 프로세스가 main의 답을 기다리는 시간. main의 서버 호출 30초(Q49) + 여유 */
export const AGENT_TOOL_TIMEOUT_MS = 35_000;

export interface AgentToolRequest {
  requestId: string;
  tool: AgentToolName;
  input: unknown;
}

/** 도구 결과. `text`는 `content[0].text`, `structuredContent`는 원본, `isError`는 MCP 규약대로 */
export interface AgentToolResult {
  text: string;
  structuredContent?: Record<string, unknown>;
  isError?: boolean;
}

export interface AgentToolError {
  code: string;
  message: string;
}

export type AgentToolOutcome = { result: AgentToolResult } | { error: AgentToolError };

export type AgentToolReply = { requestId: string } & AgentToolOutcome;

/** main → MCP 프로세스(`process.parentPort`). `ports[0]`로 도구 브리지 포트가 함께 온다 */
export interface McpStartMessage {
  type: "start";
  port: number;
  /** 연결 토큰(로드맵 Q48). CLI가 `Authorization: Bearer`로 보내는 값과 대조한다 */
  token: string;
  version: string;
  instructions: string;
}

/** MCP 프로세스 → main(`process.parentPort`) */
export type McpStatusMessage =
  | { type: "listening"; port: number }
  | { type: "listen-error"; code: string; message: string };

/** Electron `MessagePortMain`과 테스트용 가짜 포트가 공유하는 최소 모양 */
export interface PortLike {
  postMessage(message: unknown): void;
  on(event: "message", listener: (event: { data: unknown }) => void): unknown;
  start?(): void;
}

export function isAgentToolName(value: unknown): value is AgentToolName {
  return typeof value === "string" && (AGENT_TOOL_NAMES as readonly string[]).includes(value);
}

export function isAgentToolRequest(value: unknown): value is AgentToolRequest {
  if (typeof value !== "object" || value === null) return false;
  const { requestId, tool } = value as Record<string, unknown>;
  return typeof requestId === "string" && requestId.length > 0 && isAgentToolName(tool) && "input" in value;
}

export function isAgentToolReply(value: unknown): value is AgentToolReply {
  if (typeof value !== "object" || value === null) return false;
  const record = value as Record<string, unknown>;
  if (typeof record["requestId"] !== "string") return false;
  if ("result" in record) {
    const result = record["result"];
    return typeof result === "object" && result !== null && typeof (result as AgentToolResult).text === "string";
  }
  if ("error" in record) {
    const error = record["error"];
    return (
      typeof error === "object" &&
      error !== null &&
      typeof (error as AgentToolError).code === "string" &&
      typeof (error as AgentToolError).message === "string"
    );
  }
  return false;
}

export function isMcpStartMessage(value: unknown): value is McpStartMessage {
  if (typeof value !== "object" || value === null) return false;
  const { type, port, token, version, instructions } = value as Record<string, unknown>;
  return (
    type === "start" &&
    typeof port === "number" &&
    typeof token === "string" &&
    typeof version === "string" &&
    typeof instructions === "string"
  );
}

export function isMcpStatusMessage(value: unknown): value is McpStatusMessage {
  if (typeof value !== "object" || value === null) return false;
  const record = value as Record<string, unknown>;
  if (record["type"] === "listening") return typeof record["port"] === "number";
  if (record["type"] === "listen-error") {
    return typeof record["code"] === "string" && typeof record["message"] === "string";
  }
  return false;
}
