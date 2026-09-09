/**
 * CLI 에이전트 연결 브리지 — Electron 접착층 (기획서 6.4, 로드맵 Q47·Q48).
 *
 * `utilityProcess`로 MCP 서버(`dist/mcp/index.cjs`)를 띄우고 `MessageChannelMain` 한 쌍으로 도구 호출을
 * 받는다. 설정(`agent-bridge.json`)·연결 토큰·등록 스니펫·클립보드 쓰기도 여기서 한다.
 * 연결 토큰은 renderer로 내려보내지 않는다(`copyRegistration`은 클립보드에만 쓴다).
 * 순수 로직은 `bridgeConfig`·`bridgeCore`·`toolExecutor`·`registration`에 있고 여기서는 조립만 한다.
 */

import { MessageChannelMain, clipboard, utilityProcess } from "electron";
import type { UtilityProcess } from "electron";
import { copyFileSync, mkdirSync, rmSync } from "node:fs";
import { dirname, join } from "node:path";
import type { AgentBridgeStatus, AgentRegistrationTarget } from "../../shared/api";
import { isMcpStatusMessage } from "../../shared/agentProtocol";
import type { McpStartMessage } from "../../shared/agentProtocol";
import { logger } from "../logging";
import { createAgentBridgeConfigStore } from "./bridgeConfig";
import type { AgentBridgeConfigStore } from "./bridgeConfig";
import { attachToolBridge } from "./bridgeCore";
import type { BridgeCore } from "./bridgeCore";
import { MCP_SERVER_INSTRUCTIONS } from "./instructions";
import { buildRegistrationSnippet, isAgentRegistrationTarget, maskToken } from "./registration";
import type { ToolExecutor } from "./toolExecutor";

export interface AgentBridge {
  start(): void;
  stop(): void;
  getStatus(): AgentBridgeStatus;
  rotateToken(): void;
  setPort(port: number): void;
  copyRegistration(target: AgentRegistrationTarget): { preview: string };
}

export interface AgentBridgeOptions {
  userDataDir: string;
  version: string;
  /** 번들된 `dist/mcp/index.cjs` 절대 경로 */
  mcpEntryPath: string;
  /** 앱 리소스 안 SKILL.md 절대 경로. `userData/skills/knot/SKILL.md`로 복사해 둔다 */
  skillSourcePath: string;
  executor: ToolExecutor;
  configStore?: AgentBridgeConfigStore;
}

/** 폐기된 `S3`가 만들던 파일. `S8`이 있으면 지운다(기획서 6.4) */
const LEGACY_S3_FILES = ["llm-settings.json", "llm-key.bin"] as const;

export function agentServerUrl(port: number): string {
  return `http://127.0.0.1:${port}/mcp`;
}

export function createAgentBridge(options: AgentBridgeOptions): AgentBridge {
  const store = options.configStore ?? createAgentBridgeConfigStore(options.userDataDir);
  const skillPath = join(options.userDataDir, "skills", "knot", "SKILL.md");

  let child: UtilityProcess | null = null;
  let core: BridgeCore | null = null;
  let running = false;
  let error: string | null = null;
  let stopping = false;

  function removeLegacyFiles(): void {
    for (const name of LEGACY_S3_FILES) {
      rmSync(join(options.userDataDir, name), { force: true });
    }
  }

  function installSkillCopy(): void {
    try {
      mkdirSync(dirname(skillPath), { recursive: true });
      copyFileSync(options.skillSourcePath, skillPath);
    } catch (copyError) {
      logger.warn("[knot] SKILL.md 복사 실패", { reason: errorName(copyError) });
    }
  }

  function launchMcpProcess(): void {
    const config = store.read();
    error = null;
    running = false;

    const { port1, port2 } = new MessageChannelMain();
    const process = utilityProcess.fork(options.mcpEntryPath, [], {
      serviceName: "knot-mcp",
      stdio: "pipe",
    });
    child = process;

    process.stdout?.on("data", (chunk: Buffer) => {
      logger.info("[knot-mcp]", chunk.toString("utf8").trimEnd());
    });
    process.stderr?.on("data", (chunk: Buffer) => {
      logger.warn("[knot-mcp]", chunk.toString("utf8").trimEnd());
    });

    process.on("message", (message: unknown) => {
      if (!isMcpStatusMessage(message)) return;
      if (message.type === "listening") {
        running = true;
        error = null;
        logger.info("[knot] MCP 서버 기동", { port: message.port });
        return;
      }
      running = false;
      error = `${message.code}: ${message.message}`;
      logger.error("[knot] MCP 서버 기동 실패", { code: message.code, port: config.port });
    });

    process.on("exit", (code) => {
      if (child !== process) return;
      child = null;
      running = false;
      core?.abortAll();
      core = null;
      if (!stopping) {
        error = `MCP 서버 프로세스가 종료됐어요(code ${code})`;
        logger.error("[knot] MCP 서버 프로세스 종료", { code });
      }
    });

    core = attachToolBridge(port2, {
      executor: options.executor,
      log: (entry) => {
        logger.info("[knot] 도구 호출", entry);
      },
    });

    const start: McpStartMessage = {
      type: "start",
      port: config.port,
      token: config.token,
      version: options.version,
      instructions: MCP_SERVER_INSTRUCTIONS,
    };
    process.postMessage(start, [port1]);
  }

  function kill(): void {
    const process = child;
    if (process === null) return;
    child = null;
    core?.abortAll();
    core = null;
    running = false;
    process.kill();
  }

  function restart(): void {
    stopping = true;
    kill();
    stopping = false;
    launchMcpProcess();
  }

  return {
    start() {
      removeLegacyFiles();
      installSkillCopy();
      launchMcpProcess();
    },
    stop() {
      stopping = true;
      kill();
    },
    getStatus() {
      const config = store.read();
      return {
        running,
        port: config.port,
        url: agentServerUrl(config.port),
        error,
        tokenIssuedAt: config.issuedAt,
        lastToolCallAt: core?.lastToolCallAt() ?? null,
        skillPath,
      };
    },
    rotateToken() {
      store.rotateToken();
      logger.info("[knot] 연결 토큰 재발급");
      restart();
    },
    setPort(port) {
      store.setPort(port);
      logger.info("[knot] MCP 포트 변경", { port });
      restart();
    },
    copyRegistration(target) {
      if (!isAgentRegistrationTarget(target)) {
        throw new Error("알 수 없는 등록 대상이다.");
      }
      const config = store.read();
      const snippet = buildRegistrationSnippet(target, {
        url: agentServerUrl(config.port),
        token: config.token,
        skillPath,
      });
      clipboard.writeText(snippet);
      logger.info("[knot] 등록 스니펫 복사", { target });
      return { preview: maskToken(snippet, config.token) };
    },
  };
}

function errorName(error: unknown): string {
  return error instanceof Error ? error.name : "Unknown";
}
