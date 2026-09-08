import { beforeEach, describe, expect, it, vi } from "vitest";
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const userDataDir = mkdtempSync(join(tmpdir(), "knot-llm-settings-"));

const safeStorage = {
  isEncryptionAvailable: vi.fn(() => true),
  encryptString: vi.fn((value: string) => Buffer.from(value).reverse()),
  decryptString: vi.fn((buffer: Buffer) => Buffer.from(buffer).reverse().toString()),
};

vi.mock("electron", () => ({
  app: { getPath: () => userDataDir },
  safeStorage,
}));

vi.mock("electron-log/main", () => ({
  default: {
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    transports: { file: { getFile: () => ({ path: "" }) }, console: {} },
  },
}));

const {
  EMPTY_LLM_SETTINGS,
  clearLlmApiKey,
  describeLlmSettings,
  readLlmApiKey,
  readLlmSettings,
  validateLlmSettingsInput,
  writeLlmApiKey,
  writeLlmSettings,
} = await import("../src/main/llm/settingsStore");

const settingsPath = join(userDataDir, "llm-settings.json");
const keyPath = join(userDataDir, "llm-key.bin");

beforeEach(() => {
  safeStorage.isEncryptionAvailable.mockReturnValue(true);
  rmSync(settingsPath, { force: true });
  clearLlmApiKey();
});

describe("validateLlmSettingsInput", () => {
  it("provider·baseUrl·model을 검사하고 apiKey는 있을 때만 돌려준다", () => {
    expect(
      validateLlmSettingsInput({
        provider: "anthropic",
        baseUrl: " https://api.anthropic.com ",
        model: " claude-sonnet-5 ",
        apiKey: " sk-ant-x ",
      }),
    ).toEqual({
      settings: { provider: "anthropic", baseUrl: "https://api.anthropic.com", model: "claude-sonnet-5" },
      apiKey: "sk-ant-x",
    });

    expect(
      validateLlmSettingsInput({ provider: "openai-compatible", baseUrl: "http://localhost:1234/v1", model: "qwen" }),
    ).toEqual({
      settings: { provider: "openai-compatible", baseUrl: "http://localhost:1234/v1", model: "qwen" },
      apiKey: null,
    });
  });

  it("빈 apiKey는 '기존 키 유지'로 본다", () => {
    expect(
      validateLlmSettingsInput({ provider: "anthropic", baseUrl: "https://api.anthropic.com", model: "m", apiKey: "  " })
        .apiKey,
    ).toBeNull();
  });

  it("허용 범위 밖 엔드포인트·빈 model·모르는 provider·객체 아님은 거부한다 (Q27·Q44)", () => {
    expect(() =>
      validateLlmSettingsInput({ provider: "openai-compatible", baseUrl: "http://10.0.0.5:1234/v1", model: "m" }),
    ).toThrow(/baseUrl/);
    expect(() =>
      validateLlmSettingsInput({ provider: "openai-compatible", baseUrl: "http://localhost:1234/v1", model: " " }),
    ).toThrow(/model/);
    expect(() =>
      validateLlmSettingsInput({ provider: "openai", baseUrl: "https://api.openai.com/v1", model: "m" }),
    ).toThrow(/provider/);
    expect(() =>
      validateLlmSettingsInput({ provider: "anthropic", baseUrl: "https://a.com", model: "m", apiKey: 1 }),
    ).toThrow(/apiKey/);
    expect(() => validateLlmSettingsInput("문자열")).toThrow();
    expect(() => validateLlmSettingsInput(null)).toThrow();
  });
});

describe("설정 파일", () => {
  it("저장한 설정을 다시 읽고 키는 파일에 넣지 않는다", () => {
    writeLlmSettings({ provider: "openai-compatible", baseUrl: "http://localhost:1234/v1", model: "qwen" });

    expect(readLlmSettings()).toEqual({ provider: "openai-compatible", baseUrl: "http://localhost:1234/v1", model: "qwen" });
    expect(JSON.parse(readFileSync(settingsPath, "utf8"))).toEqual({
      provider: "openai-compatible",
      baseUrl: "http://localhost:1234/v1",
      model: "qwen",
    });
  });

  it("파일이 없으면 빈 설정을 돌려준다 (Q44)", () => {
    expect(readLlmSettings()).toEqual(EMPTY_LLM_SETTINGS);
    expect(describeLlmSettings()).toEqual({ provider: "openai-compatible", baseUrl: "", model: "", hasApiKey: false });
  });

  it("파일이 깨졌거나 허용 범위 밖이면 빈 설정으로 둔다", () => {
    writeFileSync(settingsPath, "{깨짐");
    expect(readLlmSettings()).toEqual(EMPTY_LLM_SETTINGS);

    writeFileSync(settingsPath, JSON.stringify({ provider: "openai-compatible", baseUrl: "http://evil:1/v1", model: "m" }));
    expect(readLlmSettings()).toEqual(EMPTY_LLM_SETTINGS);
  });
});

describe("키 저장소", () => {
  it("키를 safeStorage로 암호화해 두고 describe에는 hasApiKey만 나온다", () => {
    writeLlmApiKey("sk-ant-secret");

    expect(readLlmApiKey()).toBe("sk-ant-secret");
    expect(readFileSync(keyPath).toString("utf8")).not.toContain("sk-ant-secret");
    expect(describeLlmSettings()).toMatchObject({ hasApiKey: true });
    expect(JSON.stringify(describeLlmSettings())).not.toContain("sk-ant-secret");
  });

  it("clearLlmApiKey는 파일까지 없앤다", () => {
    writeLlmApiKey("sk-ant-secret");

    clearLlmApiKey();

    expect(existsSync(keyPath)).toBe(false);
    expect(readLlmApiKey()).toBeNull();
  });

  it("암호화를 쓸 수 없으면 메모리에만 둔다", () => {
    safeStorage.isEncryptionAvailable.mockReturnValue(false);

    writeLlmApiKey("sk-ant-secret");

    expect(existsSync(keyPath)).toBe(false);
    expect(readLlmApiKey()).toBe("sk-ant-secret");
  });
});
