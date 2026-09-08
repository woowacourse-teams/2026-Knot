/**
 * Knot 백엔드 호출 (기획서 6.4 검색 API·답변 저장 API·이력 조회).
 *
 * 토큰은 `tokenStore`에서 읽어 `Authorization: Bearer`로 붙인다(불변 계약 10번). 토큰·본문은
 * 로그에 남기지 않는다. 서버 HTTP 오류는 본문 `{code, message}`를 그대로 `KnotApiError`로
 * 옮겨 웹에 중계하고, 네트워크 오류·타임아웃은 `LLM_STREAM_FAILED`다(로드맵 Q45).
 */

import { LlmError, isAbortError } from "../llm/errors";
import { logger } from "../logging";
import type { ConversationMessage, SearchChunk } from "./prompt";

/** 서버 검색·이력·저장 호출 각각의 제한(로드맵 Q45). 현행 SSE 타임아웃과 같은 값 */
export const KNOT_API_TIMEOUT_MS = 30_000;

export type ChatSearchResponse =
  | {
      status: "READY";
      userMessageId: number;
      groundingRules: string;
      chunks: SearchChunk[];
    }
  | {
      status: "NO_RESULT" | "NEEDS_CLARIFICATION";
      userMessageId: number;
      assistantMessageId: number;
      fallbackAnswer: string;
    };

export interface SaveAssistantMessageBody {
  userMessageId: number;
  content: string;
  references: Array<Pick<SearchChunk, "importRunId" | "importedPageId" | "chunkIndex" | "score">>;
}

/** 서버가 준 HTTP 오류. `code`·`message`를 그대로 웹에 중계한다 */
export class KnotApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = "KnotApiError";
    this.status = status;
    this.code = code;
  }
}

export interface KnotApiClient {
  search(sessionId: number, content: string, signal: AbortSignal): Promise<ChatSearchResponse>;
  fetchMessages(sessionId: number, signal: AbortSignal): Promise<ConversationMessage[]>;
  saveAssistantMessage(
    sessionId: number,
    body: SaveAssistantMessageBody,
    signal: AbortSignal,
  ): Promise<{ messageId: number }>;
}

export interface KnotApiClientOptions {
  apiOrigin: string;
  readToken: () => string | null;
  fetch?: typeof fetch;
  timeoutMs?: number;
}

/** 본문을 못 읽은 HTTP 오류. 웹 SSE 경로(`streamChatMessageApi`)와 같은 코드·문구 */
const UNKNOWN_HTTP_ERROR = { code: "UNKNOWN", message: "답변 생성을 시작하지 못했어요" };

export function createKnotApiClient(options: KnotApiClientOptions): KnotApiClient {
  const doFetch = options.fetch ?? fetch;
  const timeoutMs = options.timeoutMs ?? KNOT_API_TIMEOUT_MS;

  async function request(path: string, init: RequestInit, signal: AbortSignal): Promise<unknown> {
    const token = options.readToken();
    if (token === null) {
      throw new KnotApiError(401, "UNAUTHENTICATED", "로그인이 필요합니다");
    }

    const headers: Record<string, string> = {
      Authorization: `Bearer ${token}`,
      Accept: "application/json",
    };
    if (init.body !== undefined) headers["Content-Type"] = "application/json";

    let response: Response;
    try {
      response = await doFetch(new URL(path, options.apiOrigin).toString(), {
        ...init,
        headers,
        signal: AbortSignal.any([signal, AbortSignal.timeout(timeoutMs)]),
      });
    } catch (error) {
      // 호출자가 취소한 것은 그대로 올린다. 타임아웃(TimeoutError)·네트워크 오류는 실패다
      if (signal.aborted && isAbortError(error)) throw error;
      logger.warn("[knot] 서버 호출 실패", { path, reason: errorName(error) });
      throw new LlmError("LLM_STREAM_FAILED");
    }

    if (!response.ok) {
      throw await toApiError(response, path);
    }
    if (response.status === 204) return null;
    try {
      return await response.json();
    } catch (error) {
      logger.warn("[knot] 서버 응답 본문을 읽지 못했다", { path, reason: errorName(error) });
      throw new LlmError("LLM_STREAM_FAILED");
    }
  }

  return {
    async search(sessionId, content, signal) {
      const json = await request(
        `/api/v1/conversations/${sessionId}/search`,
        { method: "POST", body: JSON.stringify({ content }) },
        signal,
      );
      return parseSearchResponse(json);
    },

    async fetchMessages(sessionId, signal) {
      const json = await request(`/api/v1/conversations/${sessionId}`, { method: "GET" }, signal);
      return parseMessages(json);
    },

    async saveAssistantMessage(sessionId, body, signal) {
      const json = await request(
        `/api/v1/conversations/${sessionId}/messages/assistant`,
        { method: "POST", body: JSON.stringify(body) },
        signal,
      );
      const messageId = (json as { messageId?: unknown } | null)?.messageId;
      if (typeof messageId !== "number") throw malformed("저장 응답");
      return { messageId };
    },
  };
}

async function toApiError(response: Response, path: string): Promise<KnotApiError> {
  let code = UNKNOWN_HTTP_ERROR.code;
  let message = UNKNOWN_HTTP_ERROR.message;
  try {
    const body = (await response.json()) as { code?: unknown; message?: unknown };
    if (typeof body.code === "string" && typeof body.message === "string") {
      code = body.code;
      message = body.message;
    }
  } catch {
    // 본문이 비었거나 JSON이 아니다
  }
  logger.warn("[knot] 서버 HTTP 오류", { path, status: response.status, code });
  return new KnotApiError(response.status, code, message);
}

function parseSearchResponse(json: unknown): ChatSearchResponse {
  const body = asObject(json, "검색 응답");
  const userMessageId = body["userMessageId"];
  if (typeof userMessageId !== "number") throw malformed("검색 응답");

  if (body["status"] === "READY") {
    const groundingRules = body["groundingRules"];
    const chunks = body["chunks"];
    if (typeof groundingRules !== "string" || !Array.isArray(chunks)) throw malformed("검색 응답");
    return { status: "READY", userMessageId, groundingRules, chunks: chunks.map(parseChunk) };
  }

  const status = body["status"];
  const assistantMessageId = body["assistantMessageId"];
  const fallbackAnswer = body["fallbackAnswer"];
  if (
    (status !== "NO_RESULT" && status !== "NEEDS_CLARIFICATION") ||
    typeof assistantMessageId !== "number" ||
    typeof fallbackAnswer !== "string"
  ) {
    throw malformed("검색 응답");
  }
  return { status, userMessageId, assistantMessageId, fallbackAnswer };
}

function parseChunk(raw: unknown): SearchChunk {
  const chunk = asObject(raw, "근거 청크");
  const { importRunId, importedPageId, chunkIndex, title, sourceUrl, content, score } = chunk;
  if (
    typeof importRunId !== "number" ||
    typeof importedPageId !== "number" ||
    typeof chunkIndex !== "number" ||
    typeof title !== "string" ||
    typeof sourceUrl !== "string" ||
    typeof content !== "string" ||
    typeof score !== "number"
  ) {
    throw malformed("근거 청크");
  }
  return { importRunId, importedPageId, chunkIndex, title, sourceUrl, content, score };
}

function parseMessages(json: unknown): ConversationMessage[] {
  if (!Array.isArray(json)) throw malformed("이력 응답");
  return json.map((raw) => {
    const message = asObject(raw, "이력 항목");
    const { id, role, content } = message;
    if (typeof id !== "number" || (role !== "USER" && role !== "ASSISTANT") || typeof content !== "string") {
      throw malformed("이력 항목");
    }
    return { id, role, content };
  });
}

function asObject(value: unknown, what: string): Record<string, unknown> {
  if (typeof value !== "object" || value === null) throw malformed(what);
  return value as Record<string, unknown>;
}

function malformed(what: string): LlmError {
  logger.warn(`[knot] ${what}의 모양이 계약과 다르다`);
  return new LlmError("LLM_STREAM_FAILED");
}

function errorName(error: unknown): string {
  return error instanceof Error ? error.name : "Unknown";
}
