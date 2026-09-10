/**
 * Anthropic Messages API 스트리밍 클라이언트 — 사용자 구독 토큰으로 직접 호출 (기획서 6.5, 로드맵 Q61 c·Q62·Q66).
 *
 * SDK 없이 Node `fetch`로 `POST https://api.anthropic.com/v1/messages`(`stream: true`)를 부르고 SSE를 직접 판다
 * (`desktop/CLAUDE.md` 코드 절 — 구독 OAuth 헤더를 SDK가 그대로 실어 주지 않는다). 헤더는 Q61 c의 여섯 개다.
 * `system`은 배열 두 블록이다 — 첫 블록은 Claude Code 식별 문장(`CLAUDE_CODE_SYSTEM_IDENTITY`), 둘째 블록이 호출자가 준
 * 규칙+근거 문자열. 식별 블록이 없으면 헤더가 다 있어도 Anthropic이 `429 rate_limit_error {message: "Error"}`로 거절한다
 * (2026-09-10 실측, 로드맵 Q61 c·U33). 실제 한도의 429와 구분하려고 HTTP 오류 본문 `message`·한도 헤더를 로그에 남긴다(Q66).
 * `temperature`·assistant prefill은 넘기지 않고 `output_config.effort`만 준다(Q62, 백엔드 `AnthropicRequestMapper`와 같다).
 *
 * 오류 단계(Q66): 응답 헤더를 받기 전(`request`)의 실패는 구독 쪽 코드(`SUBSCRIPTION_*`)로, 스트림이 열린 뒤(`stream`)의
 * 실패는 서버 SSE 경로와 같은 코드(`LLM_*`)로 낸다. 폴백 여부는 호출자(`answerFlow`)가 "첫 chunk 전인가"로 정한다.
 * 타임아웃: 응답 헤더 30초, 이벤트 사이 60초.
 *
 * 로그에는 모델·상태·지연·토큰 사용량·Anthropic 오류 본문 `message`(200자까지)만 남기고 구독 토큰·프롬프트·답변은 남기지 않는다.
 */

import { logger } from "../logging";
import type { EffortLevel } from "./llmSettings";
import type { LlmMessage } from "./promptAssembler";
import { createSseParser } from "./sseParser";
import type { SseFrame } from "./sseParser";

export const ANTHROPIC_MESSAGES_URL = "https://api.anthropic.com/v1/messages";
export const ANTHROPIC_VERSION = "2023-06-01";
/** 두 값이 함께 있어야 구독 OAuth 토큰이 Messages API에서 받아들여진다(지식 §6.9) */
export const ANTHROPIC_BETA = "claude-code-20250219,oauth-2025-04-20";
/** `user-agent: claude-cli/<ver>`의 버전. 이 PC의 Claude Code 설치본(로드맵 `S8` 실측)을 상수로 둔다(Q66) */
export const CLAUDE_CLI_VERSION = "2.1.263";
/** `system` 첫 블록. 이 문장이 첫 블록이 아니면 구독 OAuth 토큰 요청이 `429 Error`로 거절된다(로드맵 Q61 c, 2026-09-10 실측) */
export const CLAUDE_CODE_SYSTEM_IDENTITY = "You are Claude Code, Anthropic's official CLI for Claude.";
/** 로그에 남기는 Anthropic 오류 본문 `message`의 길이 상한 */
export const ERROR_MESSAGE_LOG_LIMIT = 200;
export const MESSAGES_HEADERS_TIMEOUT_MS = 30_000;
export const MESSAGES_IDLE_TIMEOUT_MS = 60_000;

export type MessagesErrorPhase = "request" | "stream";

export type MessagesErrorCode =
  | "SUBSCRIPTION_UNAUTHORIZED"
  | "SUBSCRIPTION_RATE_LIMITED"
  | "SUBSCRIPTION_REQUEST_REJECTED"
  | "SUBSCRIPTION_UNREACHABLE"
  | "LLM_STREAM_FAILED"
  | "LLM_STREAM_TIMEOUT"
  | "LLM_RATE_LIMITED"
  | "LLM_REFUSED";

export const MESSAGES_ERROR_MESSAGES: Readonly<Record<MessagesErrorCode, string>> = {
  SUBSCRIPTION_UNAUTHORIZED: "Claude 구독 인증이 거절됐어요. 다시 로그인해 주세요",
  SUBSCRIPTION_RATE_LIMITED: "Claude 구독 사용량 한도나 크레딧이 부족해요",
  SUBSCRIPTION_REQUEST_REJECTED: "Claude가 요청을 받아들이지 않았어요",
  SUBSCRIPTION_UNREACHABLE: "Claude에 연결하지 못했어요",
  LLM_STREAM_FAILED: "답변을 받는 중에 문제가 생겼어요",
  LLM_STREAM_TIMEOUT: "답변이 너무 오래 걸려 중단했어요",
  LLM_RATE_LIMITED: "Claude가 지금 바빠요. 잠시 뒤 다시 시도해 주세요",
  LLM_REFUSED: "Claude가 이 질문에 답하지 않았어요",
};

export class MessagesApiError extends Error {
  /** HTTP 오류면 상태 코드, 네트워크·스트림 오류면 null */
  readonly status: number | null;
  readonly code: MessagesErrorCode;
  readonly phase: MessagesErrorPhase;
  /** Anthropic 오류 본문·이벤트의 `error.type`. 없으면 null */
  readonly errorType: string | null;

  constructor(status: number | null, code: MessagesErrorCode, phase: MessagesErrorPhase, errorType: string | null = null) {
    super(MESSAGES_ERROR_MESSAGES[code]);
    this.name = "MessagesApiError";
    this.status = status;
    this.code = code;
    this.phase = phase;
    this.errorType = errorType;
  }
}

export interface MessagesStreamRequest {
  accessToken: string;
  model: string;
  effort: EffortLevel;
  maxTokens: number;
  system: string;
  messages: LlmMessage[];
}

export interface MessagesUsage {
  inputTokens: number;
  outputTokens: number;
}

export interface MessagesStreamResult {
  /** `text_delta`를 이어 붙인 답변 전문 */
  text: string;
  stopReason: string | null;
  usage: MessagesUsage;
  /** `message_start`가 알려 준 실제 모델 */
  model: string;
}

export interface MessagesClient {
  /**
   * 스트리밍 호출. `onDelta`는 `text_delta`마다 불린다. 호출자가 `signal`로 취소하면 그 abort 오류가 그대로 던져진다.
   * 그 밖의 실패는 `MessagesApiError`다.
   */
  stream(request: MessagesStreamRequest, onDelta: (delta: string) => void, signal: AbortSignal): Promise<MessagesStreamResult>;
}

export interface MessagesClientOptions {
  fetch?: typeof fetch;
  url?: string;
  headersTimeoutMs?: number;
  idleTimeoutMs?: number;
  cliVersion?: string;
}

/** 타임아웃으로 내부 컨트롤러를 끊었음을 표시하는 abort 사유 */
class StreamTimeout {
  constructor(readonly kind: "headers" | "idle") {}
}

export function createMessagesClient(options: MessagesClientOptions = {}): MessagesClient {
  const doFetch = options.fetch ?? fetch;
  const url = options.url ?? ANTHROPIC_MESSAGES_URL;
  const headersTimeoutMs = options.headersTimeoutMs ?? MESSAGES_HEADERS_TIMEOUT_MS;
  const idleTimeoutMs = options.idleTimeoutMs ?? MESSAGES_IDLE_TIMEOUT_MS;
  const cliVersion = options.cliVersion ?? CLAUDE_CLI_VERSION;

  return {
    async stream(request, onDelta, signal) {
      if (signal.aborted) throw signal.reason instanceof Error ? signal.reason : abortError();

      const controller = new AbortController();
      const onAbort = (): void => controller.abort(signal.reason);
      signal.addEventListener("abort", onAbort, { once: true });
      const headersTimer = setTimeout(() => controller.abort(new StreamTimeout("headers")), headersTimeoutMs);
      const startedAt = Date.now();

      let response: Response;
      try {
        response = await doFetch(url, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${request.accessToken}`,
            "anthropic-version": ANTHROPIC_VERSION,
            "anthropic-beta": ANTHROPIC_BETA,
            "user-agent": `claude-cli/${cliVersion}`,
            "x-app": "cli",
            "anthropic-dangerous-direct-browser-access": "true",
            "Content-Type": "application/json",
            Accept: "text/event-stream",
          },
          body: JSON.stringify({
            model: request.model,
            max_tokens: request.maxTokens,
            stream: true,
            system: [
              { type: "text", text: CLAUDE_CODE_SYSTEM_IDENTITY },
              { type: "text", text: request.system },
            ],
            messages: request.messages,
            output_config: { effort: request.effort },
          }),
          signal: controller.signal,
        });
      } catch (error) {
        clearTimeout(headersTimer);
        signal.removeEventListener("abort", onAbort);
        if (signal.aborted) throw error;
        logger.warn("[knot] 구독 모델 호출 실패", { model: request.model, reason: reasonOf(error, controller) });
        throw new MessagesApiError(null, "SUBSCRIPTION_UNREACHABLE", "request");
      }
      clearTimeout(headersTimer);

      if (!response.ok) {
        signal.removeEventListener("abort", onAbort);
        throw await toRequestError(response, request.model);
      }
      if (response.body === null) {
        signal.removeEventListener("abort", onAbort);
        logger.warn("[knot] 구독 모델 응답에 본문이 없다", { model: request.model, status: response.status });
        throw new MessagesApiError(response.status, "LLM_STREAM_FAILED", "stream");
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      const parser = createSseParser();
      const state: StreamState = { text: "", stopReason: null, model: "", inputTokens: 0, outputTokens: 0, stopped: false };
      let idleTimer: ReturnType<typeof setTimeout> | null = null;
      const resetIdle = (): void => {
        if (idleTimer !== null) clearTimeout(idleTimer);
        idleTimer = setTimeout(() => controller.abort(new StreamTimeout("idle")), idleTimeoutMs);
      };

      try {
        resetIdle();
        while (!state.stopped) {
          // fetch 본문은 abort 시 스스로 끊기지만, 타임아웃·취소가 읽기 대기 중에도 즉시 풀리도록 abort와 경주한다
          const { done, value } = await readWithAbort(reader, controller.signal);
          if (done) {
            for (const frame of parser.flush()) handleFrame(frame, state, onDelta);
            break;
          }
          resetIdle();
          for (const frame of parser.feed(decoder.decode(value, { stream: true }))) {
            handleFrame(frame, state, onDelta);
            if (state.stopped) break;
          }
        }
        if (!state.stopped) {
          logger.warn("[knot] 구독 모델 스트림이 message_stop 없이 끝났다", { model: request.model });
          throw new MessagesApiError(null, "LLM_STREAM_FAILED", "stream");
        }
      } catch (error) {
        if (signal.aborted) throw error;
        if (error instanceof MessagesApiError) throw error;
        if (controller.signal.aborted && controller.signal.reason instanceof StreamTimeout) {
          logger.warn("[knot] 구독 모델 스트림 타임아웃", { model: request.model, kind: controller.signal.reason.kind });
          throw new MessagesApiError(null, "LLM_STREAM_TIMEOUT", "stream");
        }
        logger.warn("[knot] 구독 모델 스트림 읽기 실패", { model: request.model, reason: errorName(error) });
        throw new MessagesApiError(null, "LLM_STREAM_FAILED", "stream");
      } finally {
        if (idleTimer !== null) clearTimeout(idleTimer);
        signal.removeEventListener("abort", onAbort);
        void reader.cancel().catch(() => undefined);
      }

      logger.info("[knot] 구독 모델 호출 완료", {
        model: state.model || request.model,
        stopReason: state.stopReason,
        latencyMs: Date.now() - startedAt,
        inputTokens: state.inputTokens,
        outputTokens: state.outputTokens,
      });
      return {
        text: state.text,
        stopReason: state.stopReason,
        usage: { inputTokens: state.inputTokens, outputTokens: state.outputTokens },
        model: state.model || request.model,
      };
    },
  };
}

/** `reader.read()`가 abort 뒤에도 매달려 있지 않도록 abort와 경주한다. abort면 그 사유로 거절된다 */
type ReadResult = Awaited<ReturnType<ReadableStreamDefaultReader<Uint8Array>["read"]>>;

function readWithAbort(reader: ReadableStreamDefaultReader<Uint8Array>, signal: AbortSignal): Promise<ReadResult> {
  if (signal.aborted) return Promise.reject(signal.reason instanceof Error ? signal.reason : abortError());
  return new Promise((resolve, reject) => {
    const onAbort = (): void => reject(signal.reason instanceof Error ? signal.reason : abortError());
    signal.addEventListener("abort", onAbort, { once: true });
    reader.read().then(
      (result) => {
        signal.removeEventListener("abort", onAbort);
        resolve(result);
      },
      (error: unknown) => {
        signal.removeEventListener("abort", onAbort);
        reject(error);
      },
    );
  });
}

interface StreamState {
  text: string;
  stopReason: string | null;
  model: string;
  inputTokens: number;
  outputTokens: number;
  stopped: boolean;
}

/** 지식 §6.4의 이벤트 표대로 처리한다. `text_delta`만 사용자에게 흘린다 */
function handleFrame(frame: SseFrame, state: StreamState, onDelta: (delta: string) => void): void {
  let event: Record<string, unknown>;
  try {
    const parsed: unknown = JSON.parse(frame.data);
    if (typeof parsed !== "object" || parsed === null) return;
    event = parsed as Record<string, unknown>;
  } catch {
    // 알 수 없는 프레임은 건너뛴다(핑 등)
    return;
  }

  switch (event["type"]) {
    case "content_block_delta": {
      const delta = asObject(event["delta"]);
      if (delta?.["type"] === "text_delta" && typeof delta["text"] === "string" && delta["text"].length > 0) {
        state.text += delta["text"];
        onDelta(delta["text"]);
      }
      return;
    }
    case "message_start": {
      const message = asObject(event["message"]);
      const usage = asObject(message?.["usage"]);
      if (typeof message?.["model"] === "string") state.model = message["model"];
      state.inputTokens = asNumber(usage?.["input_tokens"]);
      return;
    }
    case "message_delta": {
      const delta = asObject(event["delta"]);
      const usage = asObject(event["usage"]);
      state.outputTokens = asNumber(usage?.["output_tokens"]);
      const stopReason = delta?.["stop_reason"];
      if (typeof stopReason === "string") state.stopReason = stopReason;
      if (stopReason === "refusal") {
        logger.warn("[knot] 구독 모델 응답 거부");
        throw new MessagesApiError(null, "LLM_REFUSED", "stream", "refusal");
      }
      if (stopReason === "max_tokens") {
        logger.warn("[knot] 구독 모델 응답이 max_tokens에서 잘렸다", { outputTokens: state.outputTokens });
      }
      return;
    }
    case "message_stop":
      state.stopped = true;
      return;
    case "error": {
      const error = asObject(event["error"]);
      const errorType = typeof error?.["type"] === "string" ? error["type"] : "unknown";
      logger.warn("[knot] 구독 모델 스트림 오류", { type: errorType });
      throw new MessagesApiError(null, streamErrorCode(errorType), "stream", errorType);
    }
    default:
      // ping, content_block_start/stop, thinking 델타는 chunk가 아니다
      return;
  }
}

/** 백엔드 `AnthropicErrorCodes.forErrorType`과 같은 분류 */
function streamErrorCode(errorType: string): MessagesErrorCode {
  switch (errorType) {
    case "authentication_error":
    case "permission_error":
      return "SUBSCRIPTION_UNAUTHORIZED";
    case "rate_limit_error":
    case "overloaded_error":
      return "LLM_RATE_LIMITED";
    default:
      return "LLM_STREAM_FAILED";
  }
}

/** 백엔드 `AnthropicErrorCodes.forStatus`와 같은 분류를 구독 쪽 코드로 낸다(Q66) */
async function toRequestError(response: Response, model: string): Promise<MessagesApiError> {
  let errorType: string | null = null;
  let errorMessage: string | null = null;
  try {
    const body = (await response.json()) as { error?: { type?: unknown; message?: unknown } };
    if (typeof body.error?.type === "string") errorType = body.error.type;
    if (typeof body.error?.message === "string") errorMessage = body.error.message.slice(0, ERROR_MESSAGE_LOG_LIMIT);
  } catch {
    // 본문이 비었거나 JSON이 아니다
  }
  // 429가 어느 한도인지(분당 토큰·5시간·주간)는 Anthropic이 message와 한도 헤더로만 알려 준다. 본문이 `Error`뿐이면
  // 식별 블록 게이트(Q61 c)다. 사용자 질문·답변이 아니라 Anthropic이 만든 문구라 로그에 남겨도 된다(파일 머리말 로그 정책).
  logger.warn("[knot] 구독 모델 HTTP 오류", {
    model,
    status: response.status,
    type: errorType,
    message: errorMessage,
    retryAfter: response.headers.get("retry-after"),
    unifiedStatus: response.headers.get("anthropic-ratelimit-unified-status"),
    unifiedReset: response.headers.get("anthropic-ratelimit-unified-reset"),
  });
  const { status } = response;
  if (status === 401 || status === 403) return new MessagesApiError(status, "SUBSCRIPTION_UNAUTHORIZED", "request", errorType);
  if (status === 429 || status === 529) return new MessagesApiError(status, "SUBSCRIPTION_RATE_LIMITED", "request", errorType);
  if (status >= 400 && status < 500) return new MessagesApiError(status, "SUBSCRIPTION_REQUEST_REJECTED", "request", errorType);
  return new MessagesApiError(status, "LLM_STREAM_FAILED", "request", errorType);
}

function asObject(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : null;
}

function asNumber(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}

function abortError(): Error {
  const error = new Error("취소됐다.");
  error.name = "AbortError";
  return error;
}

function reasonOf(error: unknown, controller: AbortController): string {
  if (controller.signal.aborted && controller.signal.reason instanceof StreamTimeout) return `timeout:${controller.signal.reason.kind}`;
  return errorName(error);
}

function errorName(error: unknown): string {
  return error instanceof Error ? error.name : "Unknown";
}
