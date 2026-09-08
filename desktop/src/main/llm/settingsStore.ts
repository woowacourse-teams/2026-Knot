/**
 * 사용자 LLM 설정 저장소 (기획서 6.4 "설정 저장", 로드맵 Q44).
 *
 * - provider·baseUrl·model → `userData/llm-settings.json`(평문. 비밀이 아니다)
 * - apiKey → `userData/llm-key.bin`(`safeStorage` 암호화, `secretStore`)
 *
 * 키 값은 renderer로 돌아가지 않는다(`hasApiKey`만). 키·엔드포인트는 로그에 남기지 않는다.
 */

import { app } from "electron";
import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import type { UserLlmProvider, UserLlmSettings } from "../../shared/api";
import { logger } from "../logging";
import { createSecretStore } from "../secretStore";
import { isAllowedLlmEndpoint } from "./endpoint";

const SETTINGS_FILE_NAME = "llm-settings.json";
const KEY_FILE_NAME = "llm-key.bin";
const MAX_BASE_URL_LENGTH = 2048;
const MAX_MODEL_LENGTH = 200;
const MAX_API_KEY_LENGTH = 4096;

const PROVIDERS: ReadonlySet<string> = new Set<UserLlmProvider>(["openai-compatible", "anthropic"]);

/** 파일에 두는 부분. 키는 별도 저장소 */
export interface StoredLlmSettings {
  provider: UserLlmProvider;
  baseUrl: string;
  model: string;
}

/** 저장된 설정이 없을 때의 값(Q44). 반환형을 nullable로 바꾸지 않는다 */
export const EMPTY_LLM_SETTINGS: Readonly<StoredLlmSettings> = {
  provider: "openai-compatible",
  baseUrl: "",
  model: "",
};

export interface ValidatedLlmSettingsInput {
  settings: StoredLlmSettings;
  /** 값이 있으면 바꿔 쓰고, null이면 기존 키를 유지한다 */
  apiKey: string | null;
}

/**
 * renderer가 보낸 `setSettings` 인자를 검사한다. 통과하지 못하면 던진다(renderer에는 reject).
 *
 * 순수 함수다. IPC 인자는 신뢰하지 않으므로 모양·길이·허용 범위를 전부 본다.
 */
export function validateLlmSettingsInput(raw: unknown): ValidatedLlmSettingsInput {
  if (typeof raw !== "object" || raw === null) {
    throw new Error("LLM 설정은 객체여야 한다.");
  }
  const input = raw as Record<string, unknown>;

  const provider = input["provider"];
  if (typeof provider !== "string" || !PROVIDERS.has(provider)) {
    throw new Error("provider는 openai-compatible 또는 anthropic이어야 한다.");
  }

  const baseUrl = typeof input["baseUrl"] === "string" ? input["baseUrl"].trim() : "";
  if (baseUrl.length === 0 || baseUrl.length > MAX_BASE_URL_LENGTH || !isAllowedLlmEndpoint(baseUrl)) {
    throw new Error("baseUrl은 https: 주소 또는 http://localhost·http://127.0.0.1 주소여야 한다.");
  }

  const model = typeof input["model"] === "string" ? input["model"].trim() : "";
  if (model.length === 0 || model.length > MAX_MODEL_LENGTH) {
    throw new Error(`model은 1~${MAX_MODEL_LENGTH}자여야 한다.`);
  }

  const rawApiKey = input["apiKey"];
  if (rawApiKey !== undefined && typeof rawApiKey !== "string") {
    throw new Error("apiKey는 문자열이어야 한다.");
  }
  const apiKey = typeof rawApiKey === "string" ? rawApiKey.trim() : "";
  if (apiKey.length > MAX_API_KEY_LENGTH) {
    throw new Error("apiKey가 너무 길다.");
  }

  return {
    settings: { provider: provider as UserLlmProvider, baseUrl, model },
    apiKey: apiKey.length === 0 ? null : apiKey,
  };
}

const keyStore = createSecretStore(KEY_FILE_NAME, "LLM 키");

function settingsFilePath(): string {
  return join(app.getPath("userData"), SETTINGS_FILE_NAME);
}

/** 파일이 없거나 깨졌으면 빈 설정. 파일 내용도 IPC 인자처럼 검사한다(사용자가 손으로 고칠 수 있다) */
export function readLlmSettings(): StoredLlmSettings {
  let parsed: unknown;
  try {
    parsed = JSON.parse(readFileSync(settingsFilePath(), "utf8"));
  } catch (error) {
    if (!(error instanceof Error && "code" in error && error.code === "ENOENT")) {
      logger.warn("[knot] LLM 설정 파일을 읽지 못해 빈 설정으로 둔다", { reason: errorName(error) });
    }
    return { ...EMPTY_LLM_SETTINGS };
  }

  try {
    return validateLlmSettingsInput(parsed).settings;
  } catch {
    logger.warn("[knot] LLM 설정 파일 내용이 올바르지 않아 빈 설정으로 둔다");
    return { ...EMPTY_LLM_SETTINGS };
  }
}

export function writeLlmSettings(settings: StoredLlmSettings): void {
  const body: StoredLlmSettings = {
    provider: settings.provider,
    baseUrl: settings.baseUrl,
    model: settings.model,
  };
  writeFileSync(settingsFilePath(), JSON.stringify(body, null, 2), { mode: 0o600 });
}

export function readLlmApiKey(): string | null {
  return keyStore.read();
}

export function writeLlmApiKey(apiKey: string): void {
  keyStore.write(apiKey);
}

export function clearLlmApiKey(): void {
  keyStore.clear();
}

/** renderer에 돌려주는 모양. 키 값 대신 `hasApiKey` */
export function describeLlmSettings(): UserLlmSettings {
  return { ...readLlmSettings(), hasApiKey: readLlmApiKey() !== null };
}

function errorName(error: unknown): string {
  return error instanceof Error ? error.name : "Unknown";
}
