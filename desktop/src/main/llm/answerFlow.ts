/**
 * 앱 안 채팅 한 턴의 흐름 (기획서 6.5 흐름, 로드맵 `L2`, Q65·Q66).
 *
 * 구독 토큰 확인 → Workspace 검색(`S7`, 저장 없음) → (READY가 아니면 `fallbackAnswer`를 턴으로 저장하고 끝)
 * → 히스토리 조회 → `system`·`messages` 조립 → 사용자 구독으로 Messages API 스트리밍 → 답변·근거를 턴 API(`S2`)에
 * `generated_by=CLIENT`로 저장 → `complete {messageId}`.
 *
 * 폴백(Q66): 첫 `chunk` 전의 모델 쪽 실패는 `error {fallback: true}` — renderer가 서버 SSE 경로로 같은 질문을 다시
 * 보낸다. Knot 서버 오류와 첫 `chunk` 뒤의 스트림 오류는 `fallback: false`다. 취소는 이벤트를 내지 않는다.
 *
 * Electron을 import 하지 않는다 — 서버 클라이언트·구독 컨트롤러·설정·모델 클라이언트를 주입받아 vitest로 검증한다.
 * 로그에는 세션 id·모델·상태·지연·토큰 사용량만 남기고 질문·답변·구독 토큰은 남기지 않는다.
 */

import { KnotApiError, isAbortError } from "../chat/knotApi";
import type { KnotApiClient, SearchChunk, TurnReference } from "../chat/knotApi";
import { logger } from "../logging";
import { SUBSCRIPTION_MAX_TOKENS } from "./llmSettings";
import type { LlmSettingsStore } from "./llmSettings";
import { MessagesApiError } from "./messagesClient";
import type { MessagesClient } from "./messagesClient";
import { buildMessages, buildSystemPrompt } from "./promptAssembler";
import type { SubscriptionController } from "./subscriptionFlow";

/** 질문 길이 상한(서버 검색·턴 저장 API와 같다) */
export const MAX_QUESTION_LENGTH = 10_000;
/** 동시에 진행할 수 있는 스트림 수. renderer는 한 번에 하나만 보내지만 안전장치로 둔다 */
export const MAX_CONCURRENT_STREAMS = 4;

export const ANSWER_ERRORS = {
  notSignedIn: { code: "SUBSCRIPTION_NOT_SIGNED_IN", message: "Claude 구독에 로그인돼 있지 않아요" },
  emptyAnswer: { code: "LLM_STREAM_FAILED", message: "답변을 받지 못했어요" },
  tooMany: { code: "LLM_TOO_MANY_STREAMS", message: "진행 중인 답변이 너무 많아요. 잠시 뒤 다시 시도해 주세요" },
  unexpected: { code: "LLM_STREAM_FAILED", message: "답변을 받는 중에 문제가 생겼어요" },
} as const;

/** renderer가 IPC로 보내는 원본 모양(기획서 4.4 `knot:llm-stream`) */
export interface AnswerStreamInput {
  requestId: string;
  workspaceId: string;
  sessionId: string;
  content: string;
}

export interface AnswerRequest {
  workspaceId: number;
  sessionId: number;
  content: string;
}

export class AnswerInputError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "AnswerInputError";
  }
}

const REQUEST_ID_PATTERN = /^[A-Za-z0-9-]{1,64}$/;
const POSITIVE_INTEGER_PATTERN = /^[1-9][0-9]{0,17}$/;

/** IPC 입력을 다시 검사한다(renderer가 준 값은 믿지 않는다, 기획서 8절 #17) */
export function parseAnswerStreamInput(raw: unknown): AnswerStreamInput {
  if (typeof raw !== "object" || raw === null) throw new AnswerInputError("스트림 입력은 객체여야 한다.");
  const { requestId, workspaceId, sessionId, content } = raw as Record<string, unknown>;
  if (typeof requestId !== "string" || !REQUEST_ID_PATTERN.test(requestId)) {
    throw new AnswerInputError("requestId는 영숫자·하이픈 1~64자여야 한다.");
  }
  if (typeof workspaceId !== "string" || !POSITIVE_INTEGER_PATTERN.test(workspaceId)) {
    throw new AnswerInputError("workspaceId는 양의 정수 문자열이어야 한다.");
  }
  if (typeof sessionId !== "string" || !POSITIVE_INTEGER_PATTERN.test(sessionId)) {
    throw new AnswerInputError("sessionId는 양의 정수 문자열이어야 한다.");
  }
  if (typeof content !== "string" || content.trim().length === 0 || content.length > MAX_QUESTION_LENGTH) {
    throw new AnswerInputError(`content는 공백이 아닌 1~${MAX_QUESTION_LENGTH}자여야 한다.`);
  }
  return { requestId, workspaceId, sessionId, content: content.trim() };
}

export function toAnswerRequest(input: AnswerStreamInput): AnswerRequest {
  return { workspaceId: Number(input.workspaceId), sessionId: Number(input.sessionId), content: input.content };
}

export interface AnswerStreamError {
  code: string;
  message: string;
  fallback: boolean;
}

export interface AnswerEvents {
  chunk(delta: string): void;
  complete(messageId: number): void;
  error(error: AnswerStreamError): void;
}

export interface AnswerFlowDeps {
  api: KnotApiClient;
  subscription: Pick<SubscriptionController, "getAccessToken" | "recordAnswer">;
  settings: Pick<LlmSettingsStore, "read">;
  messages: MessagesClient;
  now?(): number;
}

export interface AnswerFlow {
  /** 한 턴을 끝까지 진행한다. 이벤트는 `events`로만 나가고, 취소(`signal`)면 이벤트 없이 끝난다. reject하지 않는다 */
  run(request: AnswerRequest, events: AnswerEvents, signal: AbortSignal): Promise<void>;
}

export function createAnswerFlow(deps: AnswerFlowDeps): AnswerFlow {
  const now = deps.now ?? (() => Date.now());

  return {
    async run(request, events, signal) {
      const startedAt = now();
      const log = (status: string, extra: Record<string, unknown> = {}): void => {
        logger.info("[knot] 앱 안 구독 답변", { sessionId: request.sessionId, status, latencyMs: now() - startedAt, ...extra });
      };

      const accessToken = await deps.subscription.getAccessToken();
      if (accessToken === null) {
        events.error({ ...ANSWER_ERRORS.notSignedIn, fallback: true });
        deps.subscription.recordAnswer("server-sse", ANSWER_ERRORS.notSignedIn.code);
        log("fallback:not-signed-in");
        return;
      }

      const search = await callKnot(() => deps.api.searchWorkspace(request.workspaceId, request.content, signal), signal);
      if (search.kind === "aborted") return;
      if (search.kind === "error") {
        events.error(search.error);
        log(`error:${search.error.code}`, { step: "search" });
        return;
      }

      if (search.value.status !== "READY") {
        const { fallbackAnswer } = search.value;
        const saved = await callKnot(
          () => deps.api.saveTurn(request.sessionId, { question: request.content, answer: fallbackAnswer, references: [] }, signal),
          signal,
        );
        if (saved.kind === "aborted") return;
        if (saved.kind === "error") {
          events.error(saved.error);
          log(`error:${saved.error.code}`, { step: "save-fallback" });
          return;
        }
        events.chunk(fallbackAnswer);
        events.complete(saved.value.messageId);
        deps.subscription.recordAnswer("subscription", null);
        log(`ok:${search.value.status}`);
        return;
      }

      const history = await callKnot(() => deps.api.listMessages(request.sessionId, signal), signal);
      if (history.kind === "aborted") return;
      if (history.kind === "error") {
        events.error(history.error);
        log(`error:${history.error.code}`, { step: "history" });
        return;
      }

      const settings = deps.settings.read();
      const chunks: SearchChunk[] = search.value.chunks;
      let emitted = false;
      let answer: string;
      let model: string = settings.model;
      try {
        const result = await deps.messages.stream(
          {
            accessToken,
            model: settings.model,
            effort: settings.effort,
            maxTokens: SUBSCRIPTION_MAX_TOKENS,
            system: buildSystemPrompt(search.value.groundingRules, chunks),
            messages: buildMessages(history.value, request.content),
          },
          (delta) => {
            if (signal.aborted) return;
            emitted = true;
            events.chunk(delta);
          },
          signal,
        );
        answer = result.text;
        model = result.model;
      } catch (error) {
        if (signal.aborted) return;
        const code = error instanceof MessagesApiError ? error.code : ANSWER_ERRORS.unexpected.code;
        const message = error instanceof MessagesApiError ? error.message : ANSWER_ERRORS.unexpected.message;
        const fallback = !emitted;
        events.error({ code, message, fallback });
        deps.subscription.recordAnswer(fallback ? "server-sse" : "subscription", code);
        log(`${fallback ? "fallback" : "error"}:${code}`, { step: "model", model: settings.model });
        return;
      }
      if (signal.aborted) return;
      if (answer.length === 0) {
        const fallback = !emitted;
        events.error({ ...ANSWER_ERRORS.emptyAnswer, fallback });
        deps.subscription.recordAnswer(fallback ? "server-sse" : "subscription", ANSWER_ERRORS.emptyAnswer.code);
        log(`${fallback ? "fallback" : "error"}:empty-answer`, { model });
        return;
      }

      const references: TurnReference[] = chunks.map((chunk) => ({
        importRunId: chunk.importRunId,
        importedPageId: chunk.importedPageId,
        chunkIndex: chunk.chunkIndex,
        score: chunk.score,
      }));
      const saved = await callKnot(
        () => deps.api.saveTurn(request.sessionId, { question: request.content, answer, references }, signal),
        signal,
      );
      if (saved.kind === "aborted") return;
      if (saved.kind === "error") {
        // 답변 텍스트는 이미 화면에 있다. 저장만 실패했으므로 폴백하지 않는다(Q66)
        events.error(saved.error);
        deps.subscription.recordAnswer("subscription", saved.error.code);
        log(`error:${saved.error.code}`, { step: "save", model });
        return;
      }
      events.complete(saved.value.messageId);
      deps.subscription.recordAnswer("subscription", null);
      log("ok", { model, references: references.length });
    },
  };
}

type KnotCall<T> = { kind: "ok"; value: T } | { kind: "error"; error: AnswerStreamError } | { kind: "aborted" };

/** Knot 서버 호출 실패는 서버 SSE 경로도 같은 결과이므로 폴백하지 않고 코드·문구를 그대로 낸다(Q66) */
async function callKnot<T>(call: () => Promise<T>, signal: AbortSignal): Promise<KnotCall<T>> {
  try {
    return { kind: "ok", value: await call() };
  } catch (error) {
    if (signal.aborted || isAbortError(error)) return { kind: "aborted" };
    if (error instanceof KnotApiError) {
      return { kind: "error", error: { code: error.code, message: error.message, fallback: false } };
    }
    logger.warn("[knot] 서버 호출 중 알 수 없는 오류", { reason: error instanceof Error ? error.name : "Unknown" });
    return { kind: "error", error: { ...ANSWER_ERRORS.unexpected, fallback: false } };
  }
}
