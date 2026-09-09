/**
 * MCP 프로세스 쪽 브리지: 도구 호출을 `MessagePort`로 main에 넘기고 답을 기다린다.
 *
 * requestId로 답을 대조하고, 시간 안에 답이 없으면 오류 결과로 끝낸다(앱 창이 멈춘 경우).
 * 이 파일은 Electron에 의존하지 않는다(`PortLike`).
 */

import { randomUUID } from "node:crypto";
import { AGENT_TOOL_TIMEOUT_MS, isAgentToolReply } from "../shared/agentProtocol";
import type { AgentToolName, AgentToolOutcome, PortLike } from "../shared/agentProtocol";

export interface PortRequester {
  request(tool: AgentToolName, input: unknown): Promise<AgentToolOutcome>;
  /** 답을 기다리는 요청 수(테스트용) */
  pending(): number;
}

export const BRIDGE_TIMEOUT_ERROR = {
  code: "AGENT_BRIDGE_TIMEOUT",
  message: "Knot 앱이 응답하지 않아요. 앱이 실행 중인지 확인하세요",
} as const;

export function createPortRequester(port: PortLike, options: { timeoutMs?: number } = {}): PortRequester {
  const timeoutMs = options.timeoutMs ?? AGENT_TOOL_TIMEOUT_MS;
  const waiting = new Map<string, (outcome: AgentToolOutcome) => void>();

  port.on("message", (event) => {
    const reply = event.data;
    if (!isAgentToolReply(reply)) return;
    const resolve = waiting.get(reply.requestId);
    if (resolve === undefined) return;
    waiting.delete(reply.requestId);
    resolve("result" in reply ? { result: reply.result } : { error: reply.error });
  });
  port.start?.();

  return {
    request(tool, input) {
      const requestId = randomUUID();
      return new Promise<AgentToolOutcome>((resolve) => {
        const timer = setTimeout(() => {
          if (!waiting.has(requestId)) return;
          waiting.delete(requestId);
          resolve({ error: BRIDGE_TIMEOUT_ERROR });
        }, timeoutMs);
        waiting.set(requestId, (outcome) => {
          clearTimeout(timer);
          resolve(outcome);
        });
        port.postMessage({ requestId, tool, input });
      });
    },
    pending: () => waiting.size,
  };
}
