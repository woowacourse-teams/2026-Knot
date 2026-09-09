/**
 * 앱 안 구독 호출 설정 (로드맵 Q62).
 *
 * 모델·effort를 `userData/subscription-settings.json`에 둔다. 기본은 `claude-fable-5-1`·`high`이며, 설정 화면(`L3`)이
 * 바꾼다. 선택 가능한 모델은 지식 §6.2의 목록 중 셋이다. 파일이 없거나 깨졌거나 목록 밖 값이면 기본값으로
 * 읽는다(잘못된 값이 요청에 실리지 않게). `max_tokens` 4096은 Q62의 고정값이라 설정에 두지 않는다.
 *
 * 이 파일은 비밀값이 아니므로 평문 JSON이다. 자격증명은 `subscriptionStore`에 있다. 파일 이름을 폐기된 `S3`의
 * `llm-settings.json`과 다르게 둔 이유는 `agent/bridge.ts`가 앱 시작마다 그 잔재 파일을 지우기 때문이다(로드맵 Q64).
 */

import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { logger } from "../logging";

export const LLM_SETTINGS_FILE_NAME = "subscription-settings.json";

/** 설정 화면에서 고를 수 있는 모델(로드맵 Q62, 지식 §6.2) */
export const SUBSCRIPTION_MODELS = ["claude-fable-5-1", "claude-opus-5", "claude-sonnet-5"] as const;
export type SubscriptionModel = (typeof SUBSCRIPTION_MODELS)[number];

/** Messages API `output_config.effort`의 값. 백엔드 어댑터(`B1`)와 같은 집합 */
export const EFFORT_LEVELS = ["low", "medium", "high", "xhigh", "max"] as const;
export type EffortLevel = (typeof EFFORT_LEVELS)[number];

export interface LlmSettings {
  model: SubscriptionModel;
  effort: EffortLevel;
}

export const DEFAULT_LLM_SETTINGS: LlmSettings = { model: "claude-fable-5-1", effort: "high" };

/** Q62 고정값. 설정으로 바꾸지 않는다 */
export const SUBSCRIPTION_MAX_TOKENS = 4096;

export function isSubscriptionModel(value: unknown): value is SubscriptionModel {
  return typeof value === "string" && (SUBSCRIPTION_MODELS as readonly string[]).includes(value);
}

export function isEffortLevel(value: unknown): value is EffortLevel {
  return typeof value === "string" && (EFFORT_LEVELS as readonly string[]).includes(value);
}

export interface LlmSettingsStore {
  read(): LlmSettings;
  write(settings: LlmSettings): void;
}

export class LlmSettingsInputError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "LlmSettingsInputError";
  }
}

/** renderer가 IPC로 보낸 설정 변경 입력을 허용 목록으로 다시 검사한다(`L3`, 로드맵 Q67) */
export function parseSettingsUpdate(raw: unknown): LlmSettings {
  if (typeof raw !== "object" || raw === null) throw new LlmSettingsInputError("설정은 객체여야 한다.");
  const { model, effort } = raw as Record<string, unknown>;
  if (!isSubscriptionModel(model)) {
    throw new LlmSettingsInputError(`모델은 ${SUBSCRIPTION_MODELS.join(", ")} 중 하나여야 한다.`);
  }
  if (!isEffortLevel(effort)) {
    throw new LlmSettingsInputError(`effort는 ${EFFORT_LEVELS.join(", ")} 중 하나여야 한다.`);
  }
  return { model, effort };
}

export function createLlmSettingsStore(userDataDir: string): LlmSettingsStore {
  const filePath = join(userDataDir, LLM_SETTINGS_FILE_NAME);

  return {
    read() {
      let raw: string;
      try {
        raw = readFileSync(filePath, "utf8");
      } catch {
        // 파일이 없는 것은 정상 상태(첫 실행)다
        return { ...DEFAULT_LLM_SETTINGS };
      }
      return parseSettings(raw);
    },

    write(settings) {
      if (!isSubscriptionModel(settings.model) || !isEffortLevel(settings.effort)) {
        throw new Error("모델·effort는 허용 목록 안의 값이어야 한다.");
      }
      mkdirSync(userDataDir, { recursive: true });
      writeFileSync(filePath, JSON.stringify({ model: settings.model, effort: settings.effort }, null, 2), "utf8");
      logger.info("[knot] 구독 호출 설정 저장", { model: settings.model, effort: settings.effort });
    },
  };
}

/** 깨졌거나 목록 밖 값이면 그 항목만 기본값으로 되돌린다 */
export function parseSettings(raw: string): LlmSettings {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return { ...DEFAULT_LLM_SETTINGS };
  }
  if (typeof parsed !== "object" || parsed === null) return { ...DEFAULT_LLM_SETTINGS };
  const { model, effort } = parsed as Record<string, unknown>;
  return {
    model: isSubscriptionModel(model) ? model : DEFAULT_LLM_SETTINGS.model,
    effort: isEffortLevel(effort) ? effort : DEFAULT_LLM_SETTINGS.effort,
  };
}
