import { beforeEach, describe, expect, it, vi } from "vitest";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { logMock } from "./helpers/logMock";

vi.mock("electron-log/main", () => ({ default: logMock }));

const { DEFAULT_LLM_SETTINGS, LLM_SETTINGS_FILE_NAME, LlmSettingsInputError, createLlmSettingsStore, parseSettings, parseSettingsUpdate } =
  await import("../src/main/llm/llmSettings");

let userDataDir: string;

beforeEach(() => {
  userDataDir = mkdtempSync(join(tmpdir(), "knot-llm-settings-"));
  return () => rmSync(userDataDir, { recursive: true, force: true });
});

describe("createLlmSettingsStore", () => {
  it("파일이 없으면 기본값(claude-fable-5-1·high, 로드맵 Q62)이다", () => {
    expect(createLlmSettingsStore(userDataDir).read()).toEqual({ model: "claude-fable-5-1", effort: "high" });
    expect(DEFAULT_LLM_SETTINGS).toEqual({ model: "claude-fable-5-1", effort: "high" });
  });

  it("저장한 설정을 평문 JSON으로 두고 그대로 읽는다", () => {
    const store = createLlmSettingsStore(userDataDir);

    store.write({ model: "claude-sonnet-5", effort: "medium" });

    expect(JSON.parse(readFileSync(join(userDataDir, LLM_SETTINGS_FILE_NAME), "utf8"))).toEqual({
      model: "claude-sonnet-5",
      effort: "medium",
    });
    expect(store.read()).toEqual({ model: "claude-sonnet-5", effort: "medium" });
  });

  it("목록 밖 모델·effort는 저장을 거절한다", () => {
    const store = createLlmSettingsStore(userDataDir);
    expect(() => store.write({ model: "claude-haiku-4-5" as never, effort: "high" })).toThrow();
    expect(() => store.write({ model: "claude-opus-5", effort: "ultra" as never })).toThrow();
  });

  it("깨진 파일은 기본값으로 읽는다", () => {
    writeFileSync(join(userDataDir, LLM_SETTINGS_FILE_NAME), "{not json", "utf8");
    expect(createLlmSettingsStore(userDataDir).read()).toEqual(DEFAULT_LLM_SETTINGS);
  });
});

describe("parseSettings", () => {
  it("항목별로 목록 밖 값만 기본값으로 되돌린다", () => {
    expect(parseSettings(JSON.stringify({ model: "claude-opus-5", effort: "ultra" }))).toEqual({
      model: "claude-opus-5",
      effort: "high",
    });
    expect(parseSettings(JSON.stringify({ model: "gpt-5", effort: "low" }))).toEqual({
      model: "claude-fable-5-1",
      effort: "low",
    });
    expect(parseSettings('"x"')).toEqual(DEFAULT_LLM_SETTINGS);
  });
});

describe("parseSettingsUpdate (L3, 로드맵 Q67)", () => {
  it("허용 목록 안의 모델·effort만 받는다", () => {
    expect(parseSettingsUpdate({ model: "claude-opus-5", effort: "low" })).toEqual({ model: "claude-opus-5", effort: "low" });
  });

  it.each([
    ["객체 아님", "x"],
    ["모델 목록 밖", { model: "gpt-5", effort: "high" }],
    ["effort 목록 밖", { model: "claude-opus-5", effort: "ultra" }],
    ["effort 없음", { model: "claude-opus-5" }],
  ])("잘못된 입력은 거절한다: %s", (_label, raw) => {
    expect(() => parseSettingsUpdate(raw)).toThrow(LlmSettingsInputError);
  });
});
