/**
 * 도구 실행 (로드맵 Q49). MCP 프로세스가 넘긴 `{tool, input}`을 검사하고 서버 API를 부른 뒤
 * 도구 결과 모양으로 조립한다. 실행은 전부 main에서 하고 MCP 프로세스는 서버 액세스 토큰을 모른다.
 *
 * - `list_workspaces()` → `GET /api/v1/workspaces` → 텍스트 목록 + `structuredContent.workspaces`
 * - `search_documents({query, workspaceId?})` → `POST /api/v1/workspaces/{id}/search` →
 *   `groundingRules` + `[근거 문서 n]` 블록(현행 `SearchContext.groundingPrompt`와 같은 형식) +
 *   `structuredContent {status, chunks}`. `workspaceId`가 없고 워크스페이스가 하나면 그것, 여럿이면
 *   `isError`로 목록을 돌려주고 `list_workspaces`를 안내한다. READY가 아니면 `fallbackAnswer`만.
 * - `show_answer({workspaceId, question, answer, sources, sessionId?})`(`S10`, 로드맵 Q51) → `sessionId`가 없으면
 *   `POST /api/v1/workspaces/{id}/conversations`(제목은 질문 앞 50자)로 세션을 만들고,
 *   `POST /api/v1/conversations/{sessionId}/turns`(`S2`)로 USER + ASSISTANT(`generated_by=CLIENT`) + 근거를 한 번에
 *   저장한 뒤 `{sessionId, messageId}`를 돌려준다. 저장이 끝나면 `presentAnswer`로 창을 앞으로 가져와 그 세션을 연다.
 * - 서버 오류는 `{code, message}`를 그대로 `error`로. 도구 결과에 토큰을 싣지 않는다.
 */

import { MAX_ANSWER_LENGTH, MAX_ANSWER_SOURCES, MAX_QUERY_LENGTH, SESSION_TITLE_LENGTH } from "../../shared/agentProtocol";
import type { AgentToolName, AgentToolOutcome, AgentToolResult } from "../../shared/agentProtocol";
import type { KnotDeepLink } from "../../shared/api";
import { KnotApiError, isAbortError } from "../chat/knotApi";
import type { KnotApiClient, SearchChunk, TurnReference, WorkspaceSearchResponse, WorkspaceSummary } from "../chat/knotApi";

export interface ToolExecution {
  outcome: AgentToolOutcome;
  /** 로그용. 검색·저장이 대상으로 삼은 워크스페이스. 없으면 null */
  workspaceId: number | null;
}

export interface ToolExecutor {
  execute(tool: AgentToolName, input: unknown, signal: AbortSignal): Promise<ToolExecution>;
}

export interface ToolExecutorOptions {
  /**
   * `show_answer` 저장이 끝난 뒤 호출된다(로드맵 Q51). Electron 접착층이 창을 앞으로 가져오고 preload
   * `onDeepLink`로 SPA를 그 세션으로 보낸다. 없으면 저장만 한다(테스트·헤드리스)
   */
  presentAnswer?: (link: Extract<KnotDeepLink, { type: "chat" }>) => void;
}

export const TOOL_ERRORS = {
  invalidInput: "INVALID_TOOL_INPUT",
  noWorkspace: { code: "NO_WORKSPACE", message: "속한 워크스페이스가 없어요. Knot 앱에서 워크스페이스를 만들거나 초대를 받으세요" },
  cancelled: { code: "AGENT_REQUEST_CANCELLED", message: "요청이 취소됐어요" },
} as const;

export interface SearchInput {
  query: string;
  workspaceId: number | null;
}

/** 입력을 다시 검사한다(MCP 프로세스의 zod 검사와 별개 — 심층 방어). 본문은 오류 메시지에 넣지 않는다 */
export function parseSearchInput(raw: unknown): SearchInput {
  if (typeof raw !== "object" || raw === null) {
    throw new ToolInputError("검색 입력은 객체여야 한다.");
  }
  const { query, workspaceId } = raw as Record<string, unknown>;
  if (typeof query !== "string" || query.trim().length === 0 || query.length > MAX_QUERY_LENGTH) {
    throw new ToolInputError(`query는 1~${MAX_QUERY_LENGTH}자여야 한다.`);
  }
  if (workspaceId === undefined || workspaceId === null) {
    return { query, workspaceId: null };
  }
  if (typeof workspaceId !== "number" || !Number.isInteger(workspaceId) || workspaceId <= 0) {
    throw new ToolInputError("workspaceId는 양의 정수여야 한다.");
  }
  return { query, workspaceId };
}

export class ToolInputError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ToolInputError";
  }
}

export interface ShowAnswerInput {
  workspaceId: number;
  question: string;
  answer: string;
  /** 배열 순서가 rank. `search_documents`가 돌려준 청크의 식별 필드만 */
  sources: TurnReference[];
  sessionId: number | null;
}

/** `show_answer` 입력 재검사(로드맵 Q51). 질문·답변 본문은 오류 메시지에 넣지 않는다 */
export function parseShowAnswerInput(raw: unknown): ShowAnswerInput {
  if (typeof raw !== "object" || raw === null) {
    throw new ToolInputError("show_answer 입력은 객체여야 한다.");
  }
  const { workspaceId, question, answer, sources, sessionId } = raw as Record<string, unknown>;
  if (!isPositiveInteger(workspaceId)) {
    throw new ToolInputError("workspaceId는 양의 정수여야 한다.");
  }
  if (typeof question !== "string" || question.trim().length === 0 || question.length > MAX_QUERY_LENGTH) {
    throw new ToolInputError(`question은 1~${MAX_QUERY_LENGTH}자여야 한다.`);
  }
  if (typeof answer !== "string" || answer.trim().length === 0 || answer.length > MAX_ANSWER_LENGTH) {
    throw new ToolInputError(`answer는 1~${MAX_ANSWER_LENGTH}자여야 한다.`);
  }
  if (!Array.isArray(sources) || sources.length > MAX_ANSWER_SOURCES) {
    throw new ToolInputError(`sources는 최대 ${MAX_ANSWER_SOURCES}개의 배열이어야 한다.`);
  }
  const references = sources.map(parseTurnReference);
  if (sessionId === undefined || sessionId === null) {
    return { workspaceId, question, answer, sources: references, sessionId: null };
  }
  if (!isPositiveInteger(sessionId)) {
    throw new ToolInputError("sessionId는 양의 정수여야 한다.");
  }
  return { workspaceId, question, answer, sources: references, sessionId };
}

function parseTurnReference(raw: unknown): TurnReference {
  if (typeof raw !== "object" || raw === null) {
    throw new ToolInputError("sources 항목은 객체여야 한다.");
  }
  const { importRunId, importedPageId, chunkIndex, score } = raw as Record<string, unknown>;
  if (!isPositiveInteger(importRunId) || !isPositiveInteger(importedPageId)) {
    throw new ToolInputError("sources 항목의 importRunId·importedPageId는 양의 정수여야 한다.");
  }
  if (typeof chunkIndex !== "number" || !Number.isInteger(chunkIndex) || chunkIndex < 0) {
    throw new ToolInputError("sources 항목의 chunkIndex는 0 이상의 정수여야 한다.");
  }
  if (typeof score !== "number" || !Number.isFinite(score)) {
    throw new ToolInputError("sources 항목의 score는 숫자여야 한다.");
  }
  return { importRunId, importedPageId, chunkIndex, score };
}

function isPositiveInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isInteger(value) && value > 0;
}

/** 새 세션 제목: 질문의 앞 글자(로드맵 Q51). 줄바꿈은 공백으로 */
export function sessionTitleFrom(question: string): string {
  return question.replace(/\s+/g, " ").trim().slice(0, SESSION_TITLE_LENGTH);
}

export function formatWorkspaceList(workspaces: WorkspaceSummary[]): string {
  if (workspaces.length === 0) return TOOL_ERRORS.noWorkspace.message;
  const lines = workspaces.map((workspace) =>
    workspace.role === undefined
      ? `- [${workspace.id}] ${workspace.name}`
      : `- [${workspace.id}] ${workspace.name} (${workspace.role})`,
  );
  return [`워크스페이스 ${workspaces.length}개:`, ...lines].join("\n");
}

/** 현행 `SearchContext.groundingBlock`과 같은 형식에 청크 순번 줄을 더한다(로드맵 Q49) */
export function groundingBlock(index: number, chunk: SearchChunk): string {
  return (
    `[근거 문서 ${index + 1}]\n제목: ${chunk.title}\n문서 ID: ${chunk.importedPageId}\n` +
    `문서 링크: ${chunk.sourceUrl}\n청크: ${chunk.chunkIndex}\n내용:\n${chunk.content}\n\n`
  );
}

export function formatSearchResult(response: WorkspaceSearchResponse): AgentToolResult {
  if (response.status !== "READY") {
    return {
      text: response.fallbackAnswer,
      structuredContent: { status: response.status, fallbackAnswer: response.fallbackAnswer, chunks: [] },
    };
  }
  const blocks = response.chunks.map((chunk, index) => groundingBlock(index, chunk)).join("");
  return {
    text: response.groundingRules + blocks,
    structuredContent: { status: response.status, chunks: response.chunks },
  };
}

export function createToolExecutor(api: KnotApiClient, options: ToolExecutorOptions = {}): ToolExecutor {
  async function listWorkspaces(signal: AbortSignal): Promise<ToolExecution> {
    const workspaces = await api.listWorkspaces(signal);
    return {
      workspaceId: null,
      outcome: { result: { text: formatWorkspaceList(workspaces), structuredContent: { workspaces } } },
    };
  }

  async function searchDocuments(raw: unknown, signal: AbortSignal): Promise<ToolExecution> {
    const input = parseSearchInput(raw);
    let workspaceId = input.workspaceId;

    if (workspaceId === null) {
      const workspaces = await api.listWorkspaces(signal);
      if (workspaces.length === 0) {
        return { workspaceId: null, outcome: { error: TOOL_ERRORS.noWorkspace } };
      }
      if (workspaces.length > 1) {
        return {
          workspaceId: null,
          outcome: {
            result: {
              isError: true,
              text:
                "워크스페이스가 여러 개예요. workspaceId를 지정해 다시 검색하세요(목록은 list_workspaces).\n" +
                formatWorkspaceList(workspaces),
              structuredContent: { workspaces },
            },
          },
        };
      }
      workspaceId = workspaces[0]!.id;
    }

    const response = await api.searchWorkspace(workspaceId, input.query, signal);
    return { workspaceId, outcome: { result: formatSearchResult(response) } };
  }

  async function showAnswer(raw: unknown, signal: AbortSignal): Promise<ToolExecution> {
    const input = parseShowAnswerInput(raw);
    const { workspaceId } = input;
    try {
      const sessionId =
        input.sessionId ?? (await api.createConversation(workspaceId, sessionTitleFrom(input.question), signal)).sessionId;
      const saved = await api.saveTurn(
        sessionId,
        { question: input.question, answer: input.answer, references: input.sources },
        signal,
      );
      options.presentAnswer?.({ type: "chat", workspaceId: String(workspaceId), sessionId: String(sessionId) });
      return {
        workspaceId,
        outcome: {
          result: {
            text:
              `Knot 앱에 답변을 저장하고 그 대화를 열었어요. 같은 대화에 이어서 저장하려면 다음 show_answer에 ` +
              `sessionId ${sessionId}을(를) 넘기세요.`,
            structuredContent: { workspaceId, sessionId, messageId: saved.messageId, userMessageId: saved.userMessageId },
          },
        },
      };
    } catch (error) {
      return { workspaceId, outcome: { error: toToolError(error, signal) } };
    }
  }

  return {
    async execute(tool, input, signal) {
      try {
        switch (tool) {
          case "list_workspaces":
            return await listWorkspaces(signal);
          case "search_documents":
            return await searchDocuments(input, signal);
          case "show_answer":
            return await showAnswer(input, signal);
        }
      } catch (error) {
        return { workspaceId: null, outcome: { error: toToolError(error, signal) } };
      }
    },
  };
}

function toToolError(error: unknown, signal: AbortSignal): { code: string; message: string } {
  if (error instanceof ToolInputError) {
    return { code: TOOL_ERRORS.invalidInput, message: error.message };
  }
  if (error instanceof KnotApiError) {
    return { code: error.code, message: error.message };
  }
  if (signal.aborted && isAbortError(error)) {
    return TOOL_ERRORS.cancelled;
  }
  return { code: "UNKNOWN", message: "도구 실행에 실패했어요" };
}
