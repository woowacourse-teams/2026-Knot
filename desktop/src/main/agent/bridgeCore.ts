/**
 * main 쪽 브리지 코어: `MessagePort`로 온 `{requestId, tool, input}`을 실행해 `{requestId, result|error}`로
 * 답한다. Electron에 의존하지 않아(`PortLike`) vitest로 왕복을 검증한다.
 *
 * - 동시 도구 호출은 `MAX_CONCURRENT_TOOL_CALLS`(4)까지. 넘으면 `AGENT_TOO_MANY_REQUESTS`(로드맵 Q49).
 * - 로그는 requestId·도구 이름·workspaceId·상태·지연 ms만. 질문·본문·토큰은 남기지 않는다.
 */

import { MAX_CONCURRENT_TOOL_CALLS, isAgentToolRequest } from "../../shared/agentProtocol";
import type { AgentToolName, AgentToolReply, PortLike } from "../../shared/agentProtocol";
import type { ToolExecutor } from "./toolExecutor";

export const TOO_MANY_REQUESTS_ERROR = {
  code: "AGENT_TOO_MANY_REQUESTS",
  message: "동시에 처리할 수 있는 도구 호출 수를 넘었어요. 잠시 뒤 다시 시도하세요",
} as const;

export interface ToolCallLogEntry {
  requestId: string;
  tool: AgentToolName;
  workspaceId: number | null;
  /** `ok` | `error:<코드>` | `rejected` | `invalid` */
  status: string;
  latencyMs: number;
}

export interface BridgeCoreOptions {
  executor: ToolExecutor;
  log: (entry: ToolCallLogEntry) => void;
  maxConcurrent?: number;
  now?: () => number;
}

export interface BridgeCore {
  inFlight(): number;
  /** 마지막 도구 호출 시각(ISO 8601). 없으면 null */
  lastToolCallAt(): string | null;
  /** 진행 중 요청을 전부 취소한다 */
  abortAll(): void;
}

export function attachToolBridge(port: PortLike, options: BridgeCoreOptions): BridgeCore {
  const maxConcurrent = options.maxConcurrent ?? MAX_CONCURRENT_TOOL_CALLS;
  const now = options.now ?? (() => Date.now());
  const running = new Map<string, AbortController>();
  let lastToolCallAt: string | null = null;

  const reply = (message: AgentToolReply): void => {
    port.postMessage(message);
  };

  port.on("message", (event) => {
    const request = event.data;
    if (!isAgentToolRequest(request)) {
      options.log({ requestId: "-", tool: "list_workspaces", workspaceId: null, status: "invalid", latencyMs: 0 });
      return;
    }
    const startedAt = now();
    lastToolCallAt = new Date(startedAt).toISOString();

    if (running.size >= maxConcurrent) {
      reply({ requestId: request.requestId, error: TOO_MANY_REQUESTS_ERROR });
      options.log({ requestId: request.requestId, tool: request.tool, workspaceId: null, status: "rejected", latencyMs: 0 });
      return;
    }

    const controller = new AbortController();
    running.set(request.requestId, controller);
    void options.executor.execute(request.tool, request.input, controller.signal).then((execution) => {
      running.delete(request.requestId);
      reply({ requestId: request.requestId, ...execution.outcome });
      options.log({
        requestId: request.requestId,
        tool: request.tool,
        workspaceId: execution.workspaceId,
        status: "error" in execution.outcome ? `error:${execution.outcome.error.code}` : "ok",
        latencyMs: now() - startedAt,
      });
    });
  });
  port.start?.();

  return {
    inFlight: () => running.size,
    lastToolCallAt: () => lastToolCallAt,
    abortAll() {
      for (const controller of running.values()) controller.abort();
      running.clear();
    },
  };
}
