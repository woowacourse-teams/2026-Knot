/**
 * `utilityProcess` 엔트리 (로드맵 Q47).
 *
 * main이 `process.parentPort`로 `{type: "start", port, token, version, instructions}`와 도구 브리지
 * `MessagePort`를 보내면 HTTP 서버를 연다. 도구 호출은 그 포트로 main에 위임한다. 기동 결과는
 * `{type: "listening"}` 또는 `{type: "listen-error"}`로 알린다.
 *
 * 이 프로세스는 renderer가 아니라 Node 환경이며, 서버 액세스 토큰·LLM 자격증명을 받지 않는다.
 */

/// <reference types="electron" />

import { isMcpStartMessage } from "../shared/agentProtocol";
import type { McpStatusMessage } from "../shared/agentProtocol";
import { createPortRequester } from "./portRequester";
import { startMcpHttpServer } from "./server";

const parentPort = process.parentPort;

function report(message: McpStatusMessage): void {
  parentPort.postMessage(message);
}

parentPort.once("message", (event) => {
  const message: unknown = event.data;
  const bridgePort = event.ports[0];
  if (!isMcpStartMessage(message) || bridgePort === undefined) {
    report({ type: "listen-error", code: "BAD_START_MESSAGE", message: "시작 메시지가 계약과 다르다" });
    return;
  }

  const requester = createPortRequester(bridgePort);

  startMcpHttpServer({
    port: message.port,
    token: message.token,
    version: message.version,
    instructions: message.instructions,
    requestTool: (tool, input) => requester.request(tool, input),
    onError: (error) => {
      console.error("[knot-mcp] 요청 처리 오류", errorName(error));
    },
  }).then(
    (handle) => {
      report({ type: "listening", port: handle.port });
    },
    (error: unknown) => {
      report({ type: "listen-error", code: errorCode(error), message: errorMessage(error) });
    },
  );
});

process.on("uncaughtException", (error) => {
  console.error("[knot-mcp] 처리되지 않은 예외", errorName(error));
});

/** 로그에는 종류만 남긴다. 메시지에 요청 본문이 섞일 수 있다 */
function errorName(error: unknown): string {
  return error instanceof Error ? error.name : "Unknown";
}

function errorCode(error: unknown): string {
  const code = (error as { code?: unknown } | null)?.code;
  return typeof code === "string" ? code : errorName(error);
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
