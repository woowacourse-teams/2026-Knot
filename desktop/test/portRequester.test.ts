import { describe, expect, it, vi } from "vitest";
import { attachToolBridge } from "../src/main/agent/bridgeCore";
import type { ToolExecutor } from "../src/main/agent/toolExecutor";
import { BRIDGE_TIMEOUT_ERROR, createPortRequester } from "../src/mcp/portRequester";
import { createPortPair } from "./helpers/portPair";

describe("createPortRequester (MCP 프로세스 쪽 브리지)", () => {
  it("main 브리지와 왕복해 결과를 받는다", async () => {
    const [mainPort, mcpPort] = createPortPair();
    const executor: ToolExecutor = {
      execute: vi.fn(async (tool, input) => ({
        workspaceId: null,
        outcome: { result: { text: `${tool}:${JSON.stringify(input)}` } },
      })),
    };
    attachToolBridge(mainPort, { executor, log: vi.fn() });
    const requester = createPortRequester(mcpPort);

    const [first, second] = await Promise.all([
      requester.request("list_workspaces", {}),
      requester.request("search_documents", { query: "q", workspaceId: 1 }),
    ]);

    expect(first).toEqual({ result: { text: "list_workspaces:{}" } });
    expect(second).toEqual({ result: { text: 'search_documents:{"query":"q","workspaceId":1}' } });
    expect(requester.pending()).toBe(0);
  });

  it("main이 답하지 않으면 시간 뒤 AGENT_BRIDGE_TIMEOUT 오류로 끝난다", async () => {
    const [, mcpPort] = createPortPair();
    const requester = createPortRequester(mcpPort, { timeoutMs: 20 });

    const outcome = await requester.request("list_workspaces", {});

    expect(outcome).toEqual({ error: BRIDGE_TIMEOUT_ERROR });
    expect(requester.pending()).toBe(0);
  });

  it("모르는 requestId·계약 밖 메시지는 무시한다", async () => {
    const [mainPort, mcpPort] = createPortPair();
    const requester = createPortRequester(mcpPort, { timeoutMs: 30 });
    const promise = requester.request("list_workspaces", {});

    mainPort.postMessage({ requestId: "unknown", result: { text: "x" } });
    mainPort.postMessage("noise");
    mainPort.postMessage({ requestId: "unknown", error: { code: "X" } });

    expect(await promise).toEqual({ error: BRIDGE_TIMEOUT_ERROR });
  });
});
