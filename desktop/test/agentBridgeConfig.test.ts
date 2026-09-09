import { describe, expect, it } from "vitest";
import { existsSync, mkdtempSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  AGENT_BRIDGE_FILE_NAME,
  DEFAULT_AGENT_PORT,
  createAgentBridgeConfigStore,
  generateConnectionToken,
  isValidAgentPort,
} from "../src/main/agent/bridgeConfig";

function freshDir(): string {
  return mkdtempSync(join(tmpdir(), "knot-agent-bridge-"));
}

describe("agent-bridge.json 저장소 (로드맵 Q48)", () => {
  it("처음 읽으면 기본 포트 47871과 256-bit 토큰을 발급해 0600으로 저장한다", () => {
    const dir = freshDir();
    const now = new Date("2026-09-09T00:00:00Z");
    const store = createAgentBridgeConfigStore(dir, () => now);

    const config = store.read();

    expect(config.port).toBe(DEFAULT_AGENT_PORT);
    expect(config.token).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(config.issuedAt).toBe(now.toISOString());
    const filePath = join(dir, AGENT_BRIDGE_FILE_NAME);
    expect(existsSync(filePath)).toBe(true);
    expect(statSync(filePath).mode & 0o777).toBe(0o600);
    expect(JSON.parse(readFileSync(filePath, "utf8"))).toEqual(config);
  });

  it("다시 읽으면 같은 값을 돌려준다", () => {
    const store = createAgentBridgeConfigStore(freshDir());

    expect(store.read()).toEqual(store.read());
  });

  it("토큰을 재발급하면 토큰·발급 시각만 바뀌고 포트는 유지된다", () => {
    let tick = 0;
    const store = createAgentBridgeConfigStore(freshDir(), () => new Date(Date.UTC(2026, 8, 9, 0, 0, tick++)));
    const before = store.setPort(50000);

    const after = store.rotateToken();

    expect(after.port).toBe(50000);
    expect(after.token).not.toBe(before.token);
    expect(after.issuedAt).not.toBe(before.issuedAt);
    expect(store.read()).toEqual(after);
  });

  it("포트를 바꾸면 토큰은 유지된다", () => {
    const store = createAgentBridgeConfigStore(freshDir());
    const before = store.read();

    const after = store.setPort(50001);

    expect(after).toEqual({ ...before, port: 50001 });
  });

  it("범위 밖 포트는 거부한다", () => {
    const store = createAgentBridgeConfigStore(freshDir());

    expect(() => store.setPort(80)).toThrow(/1024~65535/);
    expect(() => store.setPort(70000)).toThrow();
    expect(() => store.setPort(4787.1)).toThrow();
  });

  it("파일이 깨졌으면 새로 발급한다", () => {
    const dir = freshDir();
    writeFileSync(join(dir, AGENT_BRIDGE_FILE_NAME), "{not json");

    const config = createAgentBridgeConfigStore(dir).read();

    expect(config.port).toBe(DEFAULT_AGENT_PORT);
    expect(config.token.length).toBeGreaterThanOrEqual(43);
  });

  it("토큰이 너무 짧거나 포트가 이상한 파일도 새로 발급한다", () => {
    const dir = freshDir();
    writeFileSync(
      join(dir, AGENT_BRIDGE_FILE_NAME),
      JSON.stringify({ port: 80, token: "short", issuedAt: "2026-09-09T00:00:00Z" }),
    );

    const config = createAgentBridgeConfigStore(dir).read();

    expect(config.port).toBe(DEFAULT_AGENT_PORT);
    expect(config.token).not.toBe("short");
  });

  it("isValidAgentPort는 1024~65535 정수만 받는다", () => {
    expect(isValidAgentPort(1024)).toBe(true);
    expect(isValidAgentPort(65535)).toBe(true);
    expect(isValidAgentPort(1023)).toBe(false);
    expect(isValidAgentPort(65536)).toBe(false);
    expect(isValidAgentPort(47871.5)).toBe(false);
    expect(isValidAgentPort("47871")).toBe(false);
  });

  it("토큰은 매번 다르다", () => {
    expect(generateConnectionToken()).not.toBe(generateConnectionToken());
  });
});
