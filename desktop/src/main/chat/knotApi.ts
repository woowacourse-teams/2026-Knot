/**
 * Knot 백엔드 호출 (기획서 6.4 — 워크스페이스 목록·Workspace 검색·세션 생성·턴 저장 API).
 *
 * 로컬 MCP 서버의 도구 실행은 전부 main이 하며(로드맵 Q47), 토큰은 `tokenStore`에서 읽어
 * `Authorization: Bearer`로 붙인다(불변 계약 10번). 토큰·질문·답변·본문은 로그에 남기지 않는다.
 * 세션 생성(`POST /workspaces/{id}/conversations`)·턴 저장(`POST /conversations/{id}/turns`, `S2`)은
 * `show_answer`(`S10`, 로드맵 Q51)가 쓴다.
 *
 * 오류(로드맵 Q49): 저장된 토큰이 없으면 `UNAUTHENTICATED`("Knot 앱에 로그인하세요"), 서버 HTTP 오류는
 * 본문 `{code, message}` 그대로(본문을 못 읽으면 `UNKNOWN`), 네트워크 오류·30초 타임아웃은
 * `KNOT_API_UNREACHABLE`, 응답 모양이 계약과 다르면 `KNOT_API_MALFORMED`.
 */

import { logger } from "../logging";

/** 서버 호출 각각의 제한(로드맵 Q49). 응답 헤더가 이 안에 와야 한다 */
export const KNOT_API_TIMEOUT_MS = 30_000;

export interface WorkspaceSummary {
  id: number;
  name: string;
  /** 서버가 줄 때만 있다. 현행 목록 API에는 없다(로드맵 Q49 정정) */
  role?: string;
}

/** 검색 API가 돌려주는 근거 청크(기획서 6.4). 서버가 예산에 맞춰 `content`를 잘라 준다 */
export interface SearchChunk {
  importRunId: number;
  importedPageId: number;
  chunkIndex: number;
  title: string;
  sourceUrl: string;
  content: string;
  score: number;
}

export type WorkspaceSearchResponse =
  | { status: "READY"; groundingRules: string; chunks: SearchChunk[] }
  | { status: "NO_RESULT" | "NEEDS_CLARIFICATION"; fallbackAnswer: string };

/** 턴 저장 API의 근거 한 건(기획서 6.4 `S2`). 배열 순서가 rank다 */
export interface TurnReference {
  importRunId: number;
  importedPageId: number;
  chunkIndex: number;
  score: number;
}

/** `POST /api/v1/conversations/{sessionId}/turns` 요청 본문(기획서 6.4 `S2`) */
export interface SaveTurnRequest {
  question: string;
  answer: string;
  references: TurnReference[];
}

/** 턴 저장 응답 `201 {userMessageId, messageId}` */
export interface SavedTurn {
  userMessageId: number;
  messageId: number;
}

export const KNOT_API_ERRORS = {
  unauthenticated: { code: "UNAUTHENTICATED", message: "Knot 앱에 로그인하세요" },
  unreachable: { code: "KNOT_API_UNREACHABLE", message: "Knot 서버에 연결하지 못했어요" },
  malformed: { code: "KNOT_API_MALFORMED", message: "Knot 서버 응답이 계약과 달라요" },
  /** 본문을 못 읽은 HTTP 오류. 웹 SSE 경로(`streamChatMessageApi`)와 같은 코드·문구 */
  unknown: { code: "UNKNOWN", message: "답변 생성을 시작하지 못했어요" },
} as const;

/** 서버 호출 실패. `code`·`message`를 그대로 도구 결과로 중계한다 */
export class KnotApiError extends Error {
  /** HTTP 오류면 상태 코드, 네트워크·모양 오류면 null */
  readonly status: number | null;
  readonly code: string;

  constructor(status: number | null, code: string, message: string) {
    super(message);
    this.name = "KnotApiError";
    this.status = status;
    this.code = code;
  }
}

export interface KnotApiClient {
  listWorkspaces(signal: AbortSignal): Promise<WorkspaceSummary[]>;
  searchWorkspace(workspaceId: number, content: string, signal: AbortSignal): Promise<WorkspaceSearchResponse>;
  /** `POST /api/v1/workspaces/{id}/conversations` → 새 세션 ID. 제목은 서버 상한 255자 안이어야 한다 */
  createConversation(workspaceId: number, title: string, signal: AbortSignal): Promise<{ sessionId: number }>;
  /** `POST /api/v1/conversations/{sessionId}/turns`(`S2`) → `{userMessageId, messageId}` */
  saveTurn(sessionId: number, turn: SaveTurnRequest, signal: AbortSignal): Promise<SavedTurn>;
}

export interface KnotApiClientOptions {
  apiOrigin: string;
  readToken: () => string | null;
  fetch?: typeof fetch;
  timeoutMs?: number;
}

export function createKnotApiClient(options: KnotApiClientOptions): KnotApiClient {
  const doFetch = options.fetch ?? fetch;
  const timeoutMs = options.timeoutMs ?? KNOT_API_TIMEOUT_MS;

  async function request(path: string, init: RequestInit, signal: AbortSignal): Promise<unknown> {
    const token = options.readToken();
    if (token === null) {
      throw new KnotApiError(401, KNOT_API_ERRORS.unauthenticated.code, KNOT_API_ERRORS.unauthenticated.message);
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
      // 호출자가 취소한 것은 그대로 올린다. 타임아웃(TimeoutError)·네트워크 오류는 연결 실패다
      if (signal.aborted && isAbortError(error)) throw error;
      logger.warn("[knot] 서버 호출 실패", { path, reason: errorName(error) });
      throw new KnotApiError(null, KNOT_API_ERRORS.unreachable.code, KNOT_API_ERRORS.unreachable.message);
    }

    if (!response.ok) {
      throw await toApiError(response, path);
    }
    try {
      return await response.json();
    } catch (error) {
      logger.warn("[knot] 서버 응답 본문을 읽지 못했다", { path, reason: errorName(error) });
      throw malformed("응답 본문");
    }
  }

  return {
    async listWorkspaces(signal) {
      const json = await request("/api/v1/workspaces", { method: "GET" }, signal);
      return parseWorkspaces(json);
    },

    async searchWorkspace(workspaceId, content, signal) {
      const json = await request(
        `/api/v1/workspaces/${workspaceId}/search`,
        { method: "POST", body: JSON.stringify({ content }) },
        signal,
      );
      return parseSearchResponse(json);
    },

    async createConversation(workspaceId, title, signal) {
      const json = await request(
        `/api/v1/workspaces/${workspaceId}/conversations`,
        { method: "POST", body: JSON.stringify({ title }) },
        signal,
      );
      const body = asObject(json, "세션 생성 응답");
      const id = body["id"];
      if (typeof id !== "number") throw malformed("세션 생성 응답");
      return { sessionId: id };
    },

    async saveTurn(sessionId, turn, signal) {
      const json = await request(
        `/api/v1/conversations/${sessionId}/turns`,
        { method: "POST", body: JSON.stringify(turn) },
        signal,
      );
      const body = asObject(json, "턴 저장 응답");
      const { userMessageId, messageId } = body;
      if (typeof userMessageId !== "number" || typeof messageId !== "number") throw malformed("턴 저장 응답");
      return { userMessageId, messageId };
    },
  };
}

export function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === "AbortError";
}

async function toApiError(response: Response, path: string): Promise<KnotApiError> {
  let { code, message } = KNOT_API_ERRORS.unknown as { code: string; message: string };
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

function parseWorkspaces(json: unknown): WorkspaceSummary[] {
  const body = asObject(json, "워크스페이스 목록");
  const workspaces = body["workspaces"];
  if (!Array.isArray(workspaces)) throw malformed("워크스페이스 목록");
  return workspaces.map((raw) => {
    const item = asObject(raw, "워크스페이스 항목");
    const { id, name, role } = item;
    if (typeof id !== "number" || typeof name !== "string") throw malformed("워크스페이스 항목");
    return typeof role === "string" ? { id, name, role } : { id, name };
  });
}

function parseSearchResponse(json: unknown): WorkspaceSearchResponse {
  const body = asObject(json, "검색 응답");
  const status = body["status"];

  if (status === "READY") {
    const groundingRules = body["groundingRules"];
    const chunks = body["chunks"];
    if (typeof groundingRules !== "string" || !Array.isArray(chunks)) throw malformed("검색 응답");
    return { status: "READY", groundingRules, chunks: chunks.map(parseChunk) };
  }

  const fallbackAnswer = body["fallbackAnswer"];
  if ((status !== "NO_RESULT" && status !== "NEEDS_CLARIFICATION") || typeof fallbackAnswer !== "string") {
    throw malformed("검색 응답");
  }
  return { status, fallbackAnswer };
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

function asObject(value: unknown, what: string): Record<string, unknown> {
  if (typeof value !== "object" || value === null) throw malformed(what);
  return value as Record<string, unknown>;
}

function malformed(what: string): KnotApiError {
  logger.warn(`[knot] ${what}의 모양이 계약과 다르다`);
  return new KnotApiError(null, KNOT_API_ERRORS.malformed.code, KNOT_API_ERRORS.malformed.message);
}

function errorName(error: unknown): string {
  return error instanceof Error ? error.name : "Unknown";
}
