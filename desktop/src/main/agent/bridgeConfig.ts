/**
 * 연결 설정 저장소 `userData/agent-bridge.json` (로드맵 Q48).
 *
 * `{port, token, issuedAt}`을 0600으로 둔다. 토큰은 256-bit 난수이며 CLI 등록 스니펫에
 * `Authorization: Bearer`로 들어가는 Knot 자체의 로컬 비밀이다(LLM 자격증명이 아니다 — 계약 3번).
 * `safeStorage`로 암호화하지 않는 이유는 같은 값이 CLI 설정 파일에 평문으로 있어 이득이 없기 때문이다.
 * 파일이 없거나 깨졌으면 새로 발급한다. 값은 로그에 남기지 않는다.
 */

import { randomBytes } from "node:crypto";
import { chmodSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

export const AGENT_BRIDGE_FILE_NAME = "agent-bridge.json";
/** 기본 포트(로드맵 Q47). 임의 포트는 CLI 설정 파일의 URL이 매번 깨지므로 고정한다 */
export const DEFAULT_AGENT_PORT = 47871;
export const MIN_AGENT_PORT = 1024;
export const MAX_AGENT_PORT = 65535;

export interface AgentBridgeConfig {
  port: number;
  token: string;
  /** ISO 8601 */
  issuedAt: string;
}

export interface AgentBridgeConfigStore {
  /** 저장된 설정. 없거나 깨졌으면 새로 만들어 저장한 값 */
  read(): AgentBridgeConfig;
  /** 연결 토큰을 새로 발급한다. 기존 CLI 등록은 무효가 된다 */
  rotateToken(): AgentBridgeConfig;
  /** 포트를 바꾼다. 범위 밖이면 throw */
  setPort(port: number): AgentBridgeConfig;
  readonly filePath: string;
}

export function isValidAgentPort(value: unknown): value is number {
  return typeof value === "number" && Number.isInteger(value) && value >= MIN_AGENT_PORT && value <= MAX_AGENT_PORT;
}

/** 256-bit 난수. base64url이라 셸 명령·TOML·JSON 어디에 넣어도 따옴표 처리가 필요 없다 */
export function generateConnectionToken(): string {
  return randomBytes(32).toString("base64url");
}

export function createAgentBridgeConfigStore(
  userDataDir: string,
  now: () => Date = () => new Date(),
): AgentBridgeConfigStore {
  const filePath = join(userDataDir, AGENT_BRIDGE_FILE_NAME);

  function fresh(port: number): AgentBridgeConfig {
    return { port, token: generateConnectionToken(), issuedAt: now().toISOString() };
  }

  function write(config: AgentBridgeConfig): AgentBridgeConfig {
    mkdirSync(userDataDir, { recursive: true });
    writeFileSync(filePath, JSON.stringify(config, null, 2) + "\n", { mode: 0o600 });
    // mode 옵션은 파일을 새로 만들 때만 적용되므로 기존 파일도 0600으로 맞춘다(Windows는 무시)
    chmodSync(filePath, 0o600);
    return config;
  }

  function load(): AgentBridgeConfig | null {
    let raw: string;
    try {
      raw = readFileSync(filePath, "utf8");
    } catch {
      return null;
    }
    try {
      const parsed: unknown = JSON.parse(raw);
      if (typeof parsed !== "object" || parsed === null) return null;
      const { port, token, issuedAt } = parsed as Record<string, unknown>;
      if (!isValidAgentPort(port) || typeof token !== "string" || token.length < 32 || typeof issuedAt !== "string") {
        return null;
      }
      return { port, token, issuedAt };
    } catch {
      return null;
    }
  }

  return {
    filePath,
    read() {
      return load() ?? write(fresh(DEFAULT_AGENT_PORT));
    },
    rotateToken() {
      const current = load();
      return write(fresh(current?.port ?? DEFAULT_AGENT_PORT));
    },
    setPort(port) {
      if (!isValidAgentPort(port)) {
        throw new Error(`포트는 ${MIN_AGENT_PORT}~${MAX_AGENT_PORT} 사이의 정수여야 한다.`);
      }
      const current = load() ?? fresh(port);
      return write({ ...current, port });
    },
  };
}
