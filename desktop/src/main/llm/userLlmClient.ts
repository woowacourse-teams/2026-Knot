/**
 * 사용자 LLM 호출 진입점. provider로 클라이언트를 고른다(기획서 6.4 "사용자 LLM").
 *
 * (a) 로컬 모델 — LM Studio·Ollama 등 OpenAI 호환, 키 없음. (b) 사용자 본인 API 키 —
 * Anthropic 또는 OpenAI 호환 서비스. claude.ai 로그인·구독 OAuth·세션 토큰 중개는
 * 불변 계약 3번으로 제외하며, SDK 대신 전역 `fetch`를 직접 쓴다(기획서 9.2 금지 목록).
 */

import { streamAnthropic } from "./anthropicClient";
import { streamOpenAiCompatible } from "./openAiCompatibleClient";
import type { StoredLlmSettings } from "./settingsStore";
import type { LlmStreamRequest } from "./types";

export interface UserLlmStreamParams {
  settings: StoredLlmSettings;
  apiKey: string | null;
  request: LlmStreamRequest;
  signal: AbortSignal;
  fetch?: typeof fetch;
}

export type UserLlmStreamer = (params: UserLlmStreamParams) => AsyncGenerator<string>;

export const streamUserLlm: UserLlmStreamer = ({ settings, apiKey, request, signal, fetch: doFetch }) => {
  const options = { baseUrl: settings.baseUrl, model: settings.model, apiKey, signal, fetch: doFetch };
  switch (settings.provider) {
    case "anthropic":
      return streamAnthropic(request, options);
    case "openai-compatible":
      return streamOpenAiCompatible(request, options);
  }
};
