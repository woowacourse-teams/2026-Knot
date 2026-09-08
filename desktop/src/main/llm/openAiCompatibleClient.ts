/**
 * OpenAI 호환 `POST {baseUrl}/chat/completions` 스트리밍 클라이언트 (로드맵 Q43).
 *
 * LM Studio·Ollama·OpenAI 호환 서비스가 대상이다. 백엔드 `OpenAiCompatibleLlmStream`과
 * 같은 규칙으로 `choices[0].delta.content`만 조각으로 넘기고 `data: [DONE]`에서 끝난다.
 * 사용자 모델이 무엇이든 400이 나지 않도록 `model`·`messages`·`stream` 외의 필드는 보내지 않는다.
 */

import { chatCompletionsUrl } from "./endpoint";
import { LlmError, isAbortError, llmErrorCodeForStatus } from "./errors";
import { readSseEvents } from "./sse";
import type { LlmStreamOptions, LlmStreamRequest } from "./types";
import { logger } from "../logging";

export async function* streamOpenAiCompatible(
  request: LlmStreamRequest,
  options: LlmStreamOptions,
): AsyncGenerator<string> {
  const doFetch = options.fetch ?? fetch;
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "text/event-stream",
  };
  if (options.apiKey !== null) headers["Authorization"] = `Bearer ${options.apiKey}`;

  const messages = [
    ...(request.system.length > 0 ? [{ role: "system", content: request.system }] : []),
    ...request.messages,
  ];

  let response: Response;
  try {
    response = await doFetch(chatCompletionsUrl(options.baseUrl), {
      method: "POST",
      headers,
      body: JSON.stringify({ model: options.model, messages, stream: true }),
      signal: options.signal,
    });
  } catch (error) {
    if (isAbortError(error)) throw error;
    logger.warn("[knot] OpenAI 호환 요청 실패", { reason: errorName(error) });
    throw new LlmError("LLM_STREAM_FAILED");
  }

  if (!response.ok) {
    logger.warn("[knot] OpenAI 호환 응답 오류", { status: response.status });
    void response.body?.cancel().catch(() => undefined);
    throw new LlmError(llmErrorCodeForStatus(response.status));
  }
  if (response.body === null) throw new LlmError("LLM_STREAM_FAILED");

  let finished = false;
  for await (const event of readSseEvents(response.body)) {
    if (event.data === "[DONE]") {
      finished = true;
      break;
    }
    const { delta, finishReason } = parseChunk(event.data);
    if (delta.length > 0) yield delta;
    // 일부 서버는 [DONE] 없이 finish_reason만 준다. 그것도 정상 종료로 본다
    if (finishReason !== null) finished = true;
  }

  if (!finished) {
    logger.warn("[knot] OpenAI 호환 스트림이 종료 표시 없이 끊겼다");
    throw new LlmError("LLM_STREAM_FAILED");
  }
}

function parseChunk(data: string): { delta: string; finishReason: string | null } {
  let json: unknown;
  try {
    json = JSON.parse(data);
  } catch {
    throw new LlmError("LLM_STREAM_FAILED");
  }
  const choices = (json as { choices?: unknown }).choices;
  if (!Array.isArray(choices) || choices.length === 0) return { delta: "", finishReason: null };

  const first = choices[0] as { delta?: { content?: unknown }; finish_reason?: unknown };
  const content = first.delta?.content;
  const finishReason = first.finish_reason;
  return {
    delta: typeof content === "string" ? content : "",
    finishReason: typeof finishReason === "string" ? finishReason : null,
  };
}

function errorName(error: unknown): string {
  return error instanceof Error ? error.name : "Unknown";
}
