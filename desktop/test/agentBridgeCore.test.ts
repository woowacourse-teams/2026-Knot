import { describe, expect, it, vi } from "vitest";
import { TOO_MANY_REQUESTS_ERROR, attachToolBridge } from "../src/main/agent/bridgeCore";
import type { ToolCallLogEntry } from "../src/main/agent/bridgeCore";
import type { ToolExecution, ToolExecutor } from "../src/main/agent/toolExecutor";
import type { AgentToolReply } from "../src/shared/agentProtocol";
import { createPortPair } from "./helpers/portPair";

function deferredExecutor() {
  const pending: Array<{ resolve: (execution: ToolExecution) => void; signal: AbortSignal }> = [];
  const executor: ToolExecutor = {
    execute: vi.fn(
      (_tool, _input, signal) =>
        new Promise<ToolExecution>((resolve) => {
          pending.push({ resolve, signal });
        }),
    ),
  };
  return { executor, pending };
}

function collectReplies(port: ReturnType<typeof createPortPair>[1]): AgentToolReply[] {
  const replies: AgentToolReply[] = [];
  port.on("message", (event) => {
    replies.push(event.data as AgentToolReply);
  });
  return replies;
}

// 포트 쌍은 setImmediate로 전달하고 실행은 async라 여러 틱이 필요하다. 병렬 실행 시 5ms는 부족했다(2026-09-09 플레이크)
const flush = () => new Promise((resolve) => setTimeout(resolve, 25));

describe("attachToolBridge (main ↔ MCP 프로세스 왕복)", () => {
  it("요청을 실행해 {requestId, result}로 답하고 requestId·도구·workspaceId·상태·지연만 로그에 남긴다", async () => {
    const [mainPort, mcpPort] = createPortPair();
    const log = vi.fn<(entry: ToolCallLogEntry) => void>();
    let clock = 1_000;
    const executor: ToolExecutor = {
      execute: vi.fn(async () => {
        clock += 42;
        return { workspaceId: 7, outcome: { result: { text: "근거", structuredContent: { status: "READY", chunks: [] } } } };
      }),
    };
    const core = attachToolBridge(mainPort, { executor, log, now: () => clock });
    const replies = collectReplies(mcpPort);

    mcpPort.postMessage({ requestId: "r1", tool: "search_documents", input: { query: "비밀 질문" } });
    await flush();

    expect(replies).toEqual([{ requestId: "r1", result: { text: "근거", structuredContent: { status: "READY", chunks: [] } } }]);
    expect(executor.execute).toHaveBeenCalledWith("search_documents", { query: "비밀 질문" }, expect.any(AbortSignal));
    expect(log).toHaveBeenCalledWith({ requestId: "r1", tool: "search_documents", workspaceId: 7, status: "ok", latencyMs: 42 });
    expect(JSON.stringify(log.mock.calls)).not.toContain("비밀 질문");
    expect(core.lastToolCallAt()).toBe(new Date(1_000).toISOString());
    expect(core.inFlight()).toBe(0);
  });

  it("실행 오류는 {requestId, error}로 답하고 상태에 코드를 남긴다", async () => {
    const [mainPort, mcpPort] = createPortPair();
    const log = vi.fn();
    const executor: ToolExecutor = {
      execute: vi.fn(async () => ({ workspaceId: null, outcome: { error: { code: "UNAUTHENTICATED", message: "로그인" } } })),
    };
    attachToolBridge(mainPort, { executor, log });
    const replies = collectReplies(mcpPort);

    mcpPort.postMessage({ requestId: "r2", tool: "list_workspaces", input: {} });
    await flush();

    expect(replies).toEqual([{ requestId: "r2", error: { code: "UNAUTHENTICATED", message: "로그인" } }]);
    expect(log).toHaveBeenCalledWith(expect.objectContaining({ requestId: "r2", status: "error:UNAUTHENTICATED" }));
  });

  it("동시 4개를 넘는 요청은 즉시 AGENT_TOO_MANY_REQUESTS", async () => {
    const [mainPort, mcpPort] = createPortPair();
    const { executor, pending } = deferredExecutor();
    const core = attachToolBridge(mainPort, { executor, log: vi.fn() });
    const replies = collectReplies(mcpPort);

    for (let index = 1; index <= 5; index++) {
      mcpPort.postMessage({ requestId: `r${index}`, tool: "list_workspaces", input: {} });
    }
    await flush();

    expect(core.inFlight()).toBe(4);
    expect(replies).toEqual([{ requestId: "r5", error: TOO_MANY_REQUESTS_ERROR }]);

    for (const item of pending) item.resolve({ workspaceId: null, outcome: { result: { text: "ok" } } });
    await flush();

    expect(core.inFlight()).toBe(0);
    expect(replies).toHaveLength(5);
    // 자리가 나면 다시 받는다
    mcpPort.postMessage({ requestId: "r6", tool: "list_workspaces", input: {} });
    await flush();
    expect(core.inFlight()).toBe(1);
  });

  it("계약과 다른 메시지는 무시하고 답하지 않는다", async () => {
    const [mainPort, mcpPort] = createPortPair();
    const executor: ToolExecutor = { execute: vi.fn() };
    const log = vi.fn();
    attachToolBridge(mainPort, { executor, log });
    const replies = collectReplies(mcpPort);

    mcpPort.postMessage({ requestId: "r1", tool: "delete_everything", input: {} });
    mcpPort.postMessage("hello");
    mcpPort.postMessage({ tool: "list_workspaces", input: {} });
    await flush();

    expect(replies).toEqual([]);
    expect(executor.execute).not.toHaveBeenCalled();
    expect(log).toHaveBeenCalledTimes(3);
    expect(log).toHaveBeenLastCalledWith(expect.objectContaining({ status: "invalid" }));
  });

  it("abortAll은 진행 중 요청의 signal을 취소한다", async () => {
    const [mainPort, mcpPort] = createPortPair();
    const { executor, pending } = deferredExecutor();
    const core = attachToolBridge(mainPort, { executor, log: vi.fn() });

    mcpPort.postMessage({ requestId: "r1", tool: "list_workspaces", input: {} });
    await flush();
    core.abortAll();

    expect(pending[0]?.signal.aborted).toBe(true);
    expect(core.inFlight()).toBe(0);
  });
});
