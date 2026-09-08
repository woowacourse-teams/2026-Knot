/**
 * 탐색 데스크톱 경유의 요청 수명 (기획서 6.4 흐름 1~5, 로드맵 Q26·Q45).
 *
 * ask 한 건 = 설정 확인 → (직전 미저장 답변 재시도) → 서버 검색 → READY가 아니면 안내 문구
 * 중계 → 이력 조회 → 프롬프트 조립 → 사용자 LLM 스트리밍(`chunk`) → 서버 저장 → `complete`.
 * 세션당 진행 중 요청은 하나뿐이고, 첫 조각까지 30초를 넘기면 `LLM_STREAM_TIMEOUT`이다.
 * `cancel()` 뒤에는 이벤트를 내지 않고 저장도 하지 않는다.
 *
 * Electron에 의존하지 않는다. IPC·로그 대상은 `ipc.ts`가 주입한다.
 */

import type { ChatStreamEvent } from "../../shared/api";
import { LLM_ERROR_MESSAGES, LlmError, isAbortError } from "../llm/errors";
import type { StoredLlmSettings } from "../llm/settingsStore";
import type { UserLlmStreamer } from "../llm/userLlmClient";
import { logger } from "../logging";
import { KnotApiError, type KnotApiClient, type SaveAssistantMessageBody } from "./knotApi";
import { buildSystemPrompt, toLlmMessages } from "./prompt";

/** 첫 조각 타임아웃(로드맵 Q26). 현행 SSE 타임아웃과 같은 값 */
export const FIRST_CHUNK_TIMEOUT_MS = 30_000;

export interface ChatAskInput {
  sessionId: number;
  content: string;
}

export type ChatEventSink = (requestId: string, event: ChatStreamEvent) => void;

export interface ChatAskHandle {
  readonly requestId: string;
  cancel(): void;
  /** 이벤트가 더 나오지 않게 된 시점. 취소·완료·오류 모두 포함하며 reject하지 않는다 */
  readonly done: Promise<void>;
}

export interface ChatCoordinatorDeps {
  api: KnotApiClient;
  readSettings: () => StoredLlmSettings;
  readApiKey: () => string | null;
  streamLlm: UserLlmStreamer;
  firstChunkTimeoutMs?: number;
  generateRequestId?: () => string;
  now?: () => number;
}

export interface ChatCoordinator {
  ask(input: ChatAskInput, sink: ChatEventSink): ChatAskHandle;
  cancel(requestId: string): void;
}

interface ActiveRequest {
  requestId: string;
  cancel(): void;
}

/** 서버와 같은 코드·문구(`ChatErrorCode.CHAT_TURN_IN_PROGRESS`). 별도 코드를 만들지 않는다 */
const TURN_IN_PROGRESS = { code: "CHAT_TURN_IN_PROGRESS", message: "이전 질문의 답변이 아직 진행 중입니다" };

export function createChatCoordinator(deps: ChatCoordinatorDeps): ChatCoordinator {
  const firstChunkTimeoutMs = deps.firstChunkTimeoutMs ?? FIRST_CHUNK_TIMEOUT_MS;
  const generateRequestId = deps.generateRequestId ?? (() => crypto.randomUUID());
  const now = deps.now ?? (() => Date.now());

  const activeBySession = new Map<number, ActiveRequest>();
  const activeByRequest = new Map<string, ActiveRequest>();
  /** 저장에 실패한 답변. 그 세션의 다음 질문 직전에 한 번 재시도한다(Q23·Q45) */
  const pendingSaves = new Map<number, SaveAssistantMessageBody>();

  function ask(input: ChatAskInput, sink: ChatEventSink): ChatAskHandle {
    const requestId = generateRequestId();
    const { sessionId, content } = input;
    const controller = new AbortController();
    const state = { cancelled: false, timedOut: false, finished: false };
    const startedAt = now();
    let chunkCount = 0;
    let firstChunkAt: number | null = null;

    const emit = (event: ChatStreamEvent): void => {
      if (state.finished || state.cancelled) return;
      if (event.event !== "chunk") state.finished = true;
      sink(requestId, event);
    };
    const fail = (code: string, message: string): void => {
      emit({ event: "error", data: { code, message } });
      logOutcome("error", code);
    };
    const logOutcome = (outcome: string, code?: string): void => {
      logger.info("[knot] 탐색 요청 종료", {
        requestId,
        sessionId,
        outcome,
        ...(code === undefined ? {} : { code }),
        chunks: chunkCount,
        firstChunkMs: firstChunkAt === null ? null : firstChunkAt - startedAt,
        totalMs: now() - startedAt,
      });
    };

    const cancel = (): void => {
      if (state.cancelled || state.finished) return;
      state.cancelled = true;
      controller.abort();
      logOutcome("cancelled");
    };

    // 같은 세션에 진행 중 요청이 있으면 서버와 같은 코드로 거절한다. 등록하지 않는다
    if (activeBySession.has(sessionId)) {
      const done = new Promise<void>((resolve) => {
        setImmediate(() => {
          fail(TURN_IN_PROGRESS.code, TURN_IN_PROGRESS.message);
          resolve();
        });
      });
      return { requestId, cancel: () => undefined, done };
    }

    const active: ActiveRequest = { requestId, cancel };
    activeBySession.set(sessionId, active);
    activeByRequest.set(requestId, active);

    const run = async (): Promise<void> => {
      const signal = controller.signal;
      try {
        const settings = deps.readSettings();
        const apiKey = deps.readApiKey();
        const configurationProblem = describeConfigurationProblem(settings, apiKey);
        if (configurationProblem !== null) {
          fail("LLM_CONFIGURATION_INVALID", configurationProblem);
          return;
        }

        await retryPendingSave(sessionId, signal);

        const search = await deps.api.search(sessionId, content, signal);
        if (search.status !== "READY") {
          // 서버가 안내 문구를 이미 ASSISTANT로 저장했다. LLM을 부르지 않는다
          emit({ event: "chunk", data: { delta: search.fallbackAnswer } });
          emit({ event: "complete", data: { messageId: search.assistantMessageId } });
          logOutcome("fallback", search.status);
          return;
        }

        const history = await deps.api.fetchMessages(sessionId, signal);
        const request = {
          system: buildSystemPrompt(search.groundingRules, search.chunks),
          messages: toLlmMessages(history, content),
        };

        let answer = "";
        const timer = setTimeout(() => {
          state.timedOut = true;
          controller.abort();
        }, firstChunkTimeoutMs);
        try {
          for await (const delta of deps.streamLlm({ settings, apiKey, request, signal })) {
            if (state.cancelled) return;
            if (chunkCount === 0) {
              clearTimeout(timer);
              firstChunkAt = now();
            }
            chunkCount += 1;
            answer += delta;
            emit({ event: "chunk", data: { delta } });
          }
        } finally {
          clearTimeout(timer);
        }
        if (state.cancelled) return;

        if (chunkCount === 0) {
          logger.warn("[knot] 사용자 LLM이 조각 없이 끝났다", { requestId });
          fail("LLM_STREAM_FAILED", LLM_ERROR_MESSAGES.LLM_STREAM_FAILED);
          return;
        }

        const saveBody: SaveAssistantMessageBody = {
          userMessageId: search.userMessageId,
          content: answer,
          references: search.chunks.map(({ importRunId, importedPageId, chunkIndex, score }) => ({
            importRunId,
            importedPageId,
            chunkIndex,
            score,
          })),
        };
        try {
          const { messageId } = await deps.api.saveAssistantMessage(sessionId, saveBody, signal);
          emit({ event: "complete", data: { messageId } });
          logOutcome("complete");
        } catch (error) {
          if (state.cancelled) return;
          // 화면의 부분 답변은 남고, 다음 질문 직전에 한 번 더 저장을 시도한다
          pendingSaves.set(sessionId, saveBody);
          const { code, message } = toErrorEvent(error);
          fail(code, message);
        }
      } catch (error) {
        if (state.cancelled) return;
        if (state.timedOut) {
          fail("LLM_STREAM_TIMEOUT", LLM_ERROR_MESSAGES.LLM_STREAM_TIMEOUT);
          return;
        }
        const { code, message } = toErrorEvent(error);
        fail(code, message);
      } finally {
        activeBySession.delete(sessionId);
        activeByRequest.delete(requestId);
      }
    };

    const done = new Promise<void>((resolve) => {
      setImmediate(() => {
        run().then(resolve, (error: unknown) => {
          // run()은 스스로 모든 오류를 이벤트로 바꾼다. 여기 오면 버그다
          logger.error("[knot] 탐색 요청 처리 중 예기치 않은 오류", { requestId, reason: errorName(error) });
          activeBySession.delete(sessionId);
          activeByRequest.delete(requestId);
          fail("LLM_STREAM_FAILED", LLM_ERROR_MESSAGES.LLM_STREAM_FAILED);
          resolve();
        });
      });
    });

    return { requestId, cancel, done };
  }

  async function retryPendingSave(sessionId: number, signal: AbortSignal): Promise<void> {
    const pending = pendingSaves.get(sessionId);
    if (pending === undefined) return;
    pendingSaves.delete(sessionId);
    try {
      await deps.api.saveAssistantMessage(sessionId, pending, signal);
      logger.info("[knot] 미저장 답변을 다시 저장했다", { sessionId });
    } catch (error) {
      if (isAbortError(error)) throw error;
      // 턴이 만료됐으면 서버가 거절한다(CHAT_TURN_MISMATCH). 어느 쪽이든 다음 질문은 진행한다
      logger.warn("[knot] 미저장 답변 재저장 실패", { sessionId, code: toErrorEvent(error).code });
    }
  }

  function cancel(requestId: string): void {
    activeByRequest.get(requestId)?.cancel();
  }

  return { ask, cancel };
}

/** 질문 시점의 설정 검사(로드맵 Q44). 문제가 없으면 null, 있으면 사용자에게 보여줄 문구 */
export function describeConfigurationProblem(settings: StoredLlmSettings, apiKey: string | null): string | null {
  if (settings.baseUrl.length === 0 || settings.model.length === 0) {
    return "LLM 설정이 없습니다. 설정 화면에서 모델을 등록하세요";
  }
  if (settings.provider === "anthropic" && apiKey === null) {
    return "Anthropic API 키가 없습니다. LLM 설정에서 키를 등록하세요";
  }
  return null;
}

function toErrorEvent(error: unknown): { code: string; message: string } {
  if (error instanceof KnotApiError) return { code: error.code, message: error.message };
  if (error instanceof LlmError) return { code: error.code, message: error.message };
  return { code: "LLM_STREAM_FAILED", message: LLM_ERROR_MESSAGES.LLM_STREAM_FAILED };
}

function errorName(error: unknown): string {
  return error instanceof Error ? error.name : "Unknown";
}
