/**
 * 앱 안 구독 탐색 — Electron 접착층 (기획서 6.5, 로드맵 `L1`·`L2`).
 *
 * `L1`: `subscriptionFlow`의 상태 기계에 실제 의존성을 꽂는다 — 시스템 브라우저(`shell.openExternal`, `claude.ai` 인가 URL만),
 * loopback 서버(`A7`과 같은 것), `safeStorage` 저장소(`subscription-auth.bin`), 설정(`subscription-settings.json`).
 * 상태가 바뀌면 열린 창 전부에 `knot:llm-status-changed`를 보낸다. 상태 객체에는 토큰이 없다(기획서 4.4).
 *
 * `L2`: `answerFlow`에 서버 클라이언트·구독 컨트롤러·Messages 클라이언트를 꽂고, renderer가 시작한 스트림을
 * `requestId`로 관리한다(취소·동시 상한·앱 종료 시 정리). 이벤트는 호출한 renderer에만 보낸다(`ipc.ts`가 sender를 준다).
 */

import { BrowserWindow, shell } from "electron";
import { IPC_CHANNELS } from "../../shared/api";
import type { LlmSettingsView, LlmStreamEventPayload, LlmSubscriptionStatus } from "../../shared/api";
import { toOrigin } from "../../shared/env";
import { startLoopbackServer } from "../auth/loopbackServer";
import type { KnotApiClient } from "../chat/knotApi";
import { logger } from "../logging";
import { ANSWER_ERRORS, MAX_CONCURRENT_STREAMS, createAnswerFlow, toAnswerRequest } from "./answerFlow";
import type { AnswerStreamInput } from "./answerFlow";
import { EFFORT_LEVELS, SUBSCRIPTION_MODELS, createLlmSettingsStore, parseSettingsUpdate } from "./llmSettings";
import { createMessagesClient } from "./messagesClient";
import { createSubscriptionController } from "./subscriptionFlow";
import type { SubscriptionController } from "./subscriptionFlow";
import { SUBSCRIPTION_AUTHORIZE_URL, createSubscriptionTokenApi } from "./subscriptionOAuth";
import { createSubscriptionStore } from "./subscriptionStore";

export interface DesktopLlmOptions {
  userDataDir: string;
  /** Bearer 서버 API 클라이언트(`S8` 브리지와 같은 인스턴스) */
  api: KnotApiClient;
}

export interface DesktopLlm extends SubscriptionController {
  /** renderer가 시작한 스트림. 이벤트는 `send`로만 나간다. 같은 `requestId`가 진행 중이면 무시한다 */
  startStream(input: AnswerStreamInput, send: (payload: LlmStreamEventPayload) => void): void;
  cancelStream(requestId: string): void;
  /** `L3`: 현재 설정과 허용 목록 */
  getSettings(): LlmSettingsView;
  /** `L3`: 허용 목록으로 재검사한 뒤 저장하고 상태(model)를 다시 알린다. 목록 밖 값은 throw */
  updateSettings(raw: unknown): LlmSettingsView;
}

export function createDesktopLlm(options: DesktopLlmOptions): DesktopLlm {
  const authorizeOrigin = toOrigin(SUBSCRIPTION_AUTHORIZE_URL);
  const settings = createLlmSettingsStore(options.userDataDir);

  function broadcast(status: LlmSubscriptionStatus): void {
    for (const window of BrowserWindow.getAllWindows()) {
      if (window.isDestroyed()) continue;
      window.webContents.send(IPC_CHANNELS.llmStatusChanged, status);
    }
  }

  const subscription = createSubscriptionController({
    api: createSubscriptionTokenApi(),
    credentials: createSubscriptionStore(),
    settings,
    startLoopback: startLoopbackServer,
    openExternal: async (url) => {
      // 인가 URL은 고정 상수로 조립한 값이다. 그 밖의 오리진은 열지 않는다
      if (toOrigin(url) !== authorizeOrigin) {
        throw new Error("claude.ai 인가 URL만 시스템 브라우저로 연다.");
      }
      await shell.openExternal(url);
    },
    onStatusChanged: (status) => {
      logger.info("[knot] 구독 상태 변경", {
        signedIn: status.signedIn,
        model: status.model,
        lastError: status.lastError,
        lastAnsweredBy: status.lastAnsweredBy,
      });
      broadcast(status);
    },
  });

  const flow = createAnswerFlow({ api: options.api, subscription, settings, messages: createMessagesClient() });
  const active = new Map<string, AbortController>();

  function startStream(input: AnswerStreamInput, send: (payload: LlmStreamEventPayload) => void): void {
    const { requestId } = input;
    if (active.has(requestId)) {
      logger.warn("[knot] 이미 진행 중인 스트림 요청 무시", { requestId });
      return;
    }
    if (active.size >= MAX_CONCURRENT_STREAMS) {
      send({ requestId, event: "error", ...ANSWER_ERRORS.tooMany, fallback: false });
      return;
    }
    const controller = new AbortController();
    active.set(requestId, controller);
    logger.info("[knot] 앱 안 구독 답변 시작", { requestId, sessionId: Number(input.sessionId) });

    flow
      .run(
        toAnswerRequest(input),
        {
          chunk: (delta) => send({ requestId, event: "chunk", delta }),
          complete: (messageId) => send({ requestId, event: "complete", messageId }),
          error: (error) => send({ requestId, event: "error", ...error }),
        },
        controller.signal,
      )
      .catch((error: unknown) => {
        // answerFlow는 reject하지 않지만 혹시 모를 예외를 renderer에 알린다
        logger.error("[knot] 앱 안 구독 답변 예외", { requestId, reason: error instanceof Error ? error.name : "Unknown" });
        if (!controller.signal.aborted) send({ requestId, event: "error", ...ANSWER_ERRORS.unexpected, fallback: false });
      })
      .finally(() => {
        active.delete(requestId);
      });
  }

  function cancelStream(requestId: string): void {
    const controller = active.get(requestId);
    if (controller === undefined) return;
    active.delete(requestId);
    controller.abort();
    logger.info("[knot] 앱 안 구독 답변 취소", { requestId });
  }

  function getSettings(): LlmSettingsView {
    return { ...settings.read(), models: SUBSCRIPTION_MODELS, efforts: EFFORT_LEVELS };
  }

  function updateSettings(raw: unknown): LlmSettingsView {
    settings.write(parseSettingsUpdate(raw));
    subscription.emitStatus();
    return getSettings();
  }

  return {
    ...subscription,
    startStream,
    cancelStream,
    getSettings,
    updateSettings,
    dispose() {
      for (const [requestId, controller] of active) {
        controller.abort();
        active.delete(requestId);
      }
      subscription.dispose();
    },
  };
}
