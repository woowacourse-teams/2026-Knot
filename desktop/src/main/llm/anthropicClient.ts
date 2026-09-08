/**
 * Anthropic Messages API `POST {baseUrl}/v1/messages` 스트리밍 클라이언트 (로드맵 Q43).
 *
 * 백엔드 `AnthropicLlmStream`과 같은 규칙이다 — `content_block_delta.text_delta`만 조각으로
 * 넘기고 `message_stop`에서 끝난다. `stop_reason=refusal`은 `LLM_REFUSED`, 스트림 `error`
 * 이벤트는 `error.type`으로 매핑한다. 사용량(토큰 수)은 INFO 로그로만 남긴다.
 *
 * 사용자 모델이 무엇이든 400이 나지 않도록 `temperature`·`thinking`·`output_config`는
 * 보내지 않는다(서버 어댑터의 `output_config.effort`는 Opus 5 전용).
 */

import { anthropicMessagesUrl } from "./endpoint";
import { LlmError, isAbortError, llmErrorCodeForAnthropicErrorType, llmErrorCodeForStatus } from "./errors";
import { readSseEvents } from "./sse";
import type { LlmStreamOptions, LlmStreamRequest } from "./types";
import { logger } from "../logging";

const ANTHROPIC_VERSION = "2023-06-01";
/** Messages API의 필수 필드. 서버 어댑터 기본값(`llm.anthropic.max-tokens`)과 같다 */
const MAX_TOKENS = 4096;
const MAX_ERROR_BODY_BYTES = 4096;

export async function* streamAnthropic(
  request: LlmStreamRequest,
  options: LlmStreamOptions,
): AsyncGenerator<string> {
  if (options.apiKey === null) {
    throw new LlmError("LLM_CONFIGURATION_INVALID", "Anthropic API 키가 없습니다. LLM 설정에서 키를 등록하세요");
  }
  const doFetch = options.fetch ?? fetch;

  const payload: Record<string, unknown> = {
    model: options.model,
    max_tokens: MAX_TOKENS,
    stream: true,
    messages: request.messages,
  };
  if (request.system.length > 0) payload["system"] = request.system;

  let response: Response;
  try {
    response = await doFetch(anthropicMessagesUrl(options.baseUrl), {
      method: "POST",
      headers: {
        "x-api-key": options.apiKey,
        "anthropic-version": ANTHROPIC_VERSION,
        "Content-Type": "application/json",
        Accept: "text/event-stream",
      },
      body: JSON.stringify(payload),
      signal: options.signal,
    });
  } catch (error) {
    if (isAbortError(error)) throw error;
    logger.warn("[knot] Anthropic 요청 실패", { reason: errorName(error) });
    throw new LlmError("LLM_STREAM_FAILED");
  }

  if (!response.ok) {
    logger.warn("[knot] Anthropic 응답 오류", {
      status: response.status,
      type: await errorType(response),
      retryAfter: response.headers.get("retry-after") ?? "-",
    });
    throw new LlmError(llmErrorCodeForStatus(response.status));
  }
  if (response.body === null) throw new LlmError("LLM_STREAM_FAILED");

  const usage = { model: "", inputTokens: 0, cacheReadInputTokens: 0, outputTokens: 0 };
  let stopped = false;

  for await (const sse of readSseEvents(response.body)) {
    const event = parseEvent(sse.data);
    switch (event.type) {
      case "content_block_delta": {
        const delta = event.delta;
        if (delta?.type === "text_delta" && typeof delta.text === "string" && delta.text.length > 0) {
          yield delta.text;
        }
        break;
      }
      case "message_start": {
        usage.model = stringOf(event.message?.model);
        usage.inputTokens = numberOf(event.message?.usage?.input_tokens);
        usage.cacheReadInputTokens = numberOf(event.message?.usage?.cache_read_input_tokens);
        break;
      }
      case "message_delta": {
        usage.outputTokens = numberOf(event.usage?.output_tokens);
        logger.info("[knot] Anthropic 사용량", usage);
        const stopReason = event.delta?.stop_reason;
        if (stopReason === "refusal") {
          logger.warn("[knot] Anthropic 응답 거부", { category: stringOf(event.delta?.stop_details?.category) || "unknown" });
          throw new LlmError("LLM_REFUSED");
        }
        if (stopReason === "max_tokens") {
          logger.warn("[knot] Anthropic 응답이 max_tokens에서 잘렸다", { outputTokens: usage.outputTokens });
        }
        break;
      }
      case "message_stop":
        stopped = true;
        break;
      case "error": {
        const type = stringOf(event.error?.type) || "unknown";
        logger.warn("[knot] Anthropic 스트림 오류", { type });
        throw new LlmError(llmErrorCodeForAnthropicErrorType(type));
      }
      default:
        // ping, content_block_start/stop, thinking 델타는 조각이 아니다
        break;
    }
    if (stopped) break;
  }

  if (!stopped) {
    logger.warn("[knot] Anthropic 스트림이 message_stop 없이 끊겼다");
    throw new LlmError("LLM_STREAM_FAILED");
  }
}

interface AnthropicEvent {
  type?: string;
  delta?: { type?: string; text?: unknown; stop_reason?: unknown; stop_details?: { category?: unknown } };
  message?: { model?: unknown; usage?: { input_tokens?: unknown; cache_read_input_tokens?: unknown } };
  usage?: { output_tokens?: unknown };
  error?: { type?: unknown };
}

function parseEvent(data: string): AnthropicEvent {
  try {
    const json: unknown = JSON.parse(data);
    return typeof json === "object" && json !== null ? (json as AnthropicEvent) : {};
  } catch {
    throw new LlmError("LLM_STREAM_FAILED");
  }
}

/** 오류 본문에서 `error.type`만 읽는다. 본문 전체는 로그에 남기지 않는다 */
async function errorType(response: Response): Promise<string> {
  try {
    const text = (await response.text()).slice(0, MAX_ERROR_BODY_BYTES);
    const json = JSON.parse(text) as { error?: { type?: unknown } };
    return stringOf(json.error?.type) || "unknown";
  } catch {
    return "unknown";
  }
}

function stringOf(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function numberOf(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}

function errorName(error: unknown): string {
  return error instanceof Error ? error.name : "Unknown";
}
