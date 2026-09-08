/**
 * main 쪽 IPC 핸들러.
 *
 * 모든 `ipcMain.handle`은 `event.senderFrame`의 오리진이 웹 오리진일 때만
 * 처리한다(기획서 4.4·8절 #17). `senderFrame`이 null이면 거부한다.
 * 인자·반환값 중 토큰·LLM 키·질문·답변 본문은 로그에 남기지 않는다.
 */

import { ipcMain } from "electron";
import type { IpcMainInvokeEvent, WebContents } from "electron";
import { IPC_CHANNELS } from "../shared/api";
import type { ChatStreamEvent, KnotDeepLink, UserLlmSettings } from "../shared/api";
import type { KnotEnvironment } from "../shared/env";
import { toOrigin } from "../shared/env";
import { createChatCoordinator } from "./chat/chatService";
import type { ChatAskInput, ChatCoordinator } from "./chat/chatService";
import { createKnotApiClient } from "./chat/knotApi";
import {
  clearLlmApiKey,
  describeLlmSettings,
  readLlmApiKey,
  readLlmSettings,
  validateLlmSettingsInput,
  writeLlmApiKey,
  writeLlmSettings,
} from "./llm/settingsStore";
import { streamUserLlm } from "./llm/userLlmClient";
import { logger } from "./logging";
import { openExternalUrl } from "./navigation";
import { clearToken, readToken, writeToken } from "./tokenStore";

/** 검색 API의 `content` 상한과 같다(`SearchChatMessageRequest`) */
const MAX_CONTENT_LENGTH = 10_000;

function assertTrustedSender(event: IpcMainInvokeEvent, env: KnotEnvironment, channel: string): void {
  const frame = event.senderFrame;
  if (frame === null) {
    logger.warn("[knot] IPC 거부: senderFrame 없음", { channel });
    throw new Error("허용되지 않은 호출자다.");
  }
  if (toOrigin(frame.url) !== env.webOrigin) {
    logger.warn("[knot] IPC 거부: 오리진 불일치", { channel, url: frame.url });
    throw new Error("허용되지 않은 호출자다.");
  }
}

/** renderer가 보낸 `chat.ask` 인자를 검사한다. 본문은 로그에 남기지 않는다 */
export function parseChatAskInput(raw: unknown): ChatAskInput {
  if (typeof raw !== "object" || raw === null) {
    throw new Error("질문 입력은 객체여야 한다.");
  }
  const { sessionId, content } = raw as Record<string, unknown>;
  if (typeof sessionId !== "number" || !Number.isInteger(sessionId) || sessionId <= 0) {
    throw new Error("sessionId는 양의 정수여야 한다.");
  }
  if (typeof content !== "string" || content.trim().length === 0 || content.length > MAX_CONTENT_LENGTH) {
    throw new Error(`content는 1~${MAX_CONTENT_LENGTH}자여야 한다.`);
  }
  return { sessionId, content };
}

export function createDefaultChatCoordinator(env: KnotEnvironment): ChatCoordinator {
  return createChatCoordinator({
    api: createKnotApiClient({ apiOrigin: env.apiOrigin, readToken }),
    readSettings: readLlmSettings,
    readApiKey: readLlmApiKey,
    streamLlm: streamUserLlm,
  });
}

export function registerIpcHandlers(
  env: KnotEnvironment,
  coordinator: ChatCoordinator = createDefaultChatCoordinator(env),
): void {
  ipcMain.handle(IPC_CHANNELS.openExternal, async (event, rawUrl: unknown) => {
    assertTrustedSender(event, env, IPC_CHANNELS.openExternal);
    if (typeof rawUrl !== "string") {
      throw new Error("URL은 문자열이어야 한다.");
    }
    await openExternalUrl(rawUrl);
  });

  // 딥링크 수신은 A8에서 붙인다. 계약(기획서 4.4)을 먼저 고정해 두고
  // 지금은 항상 null을 돌려준다.
  ipcMain.handle(IPC_CHANNELS.getPendingDeepLink, (event): KnotDeepLink | null => {
    assertTrustedSender(event, env, IPC_CHANNELS.getPendingDeepLink);
    return null;
  });

  // 토큰 세 채널은 저장소만 연다. 인자·반환값을 로그에 남기지 않는다(기획서 4.4).
  ipcMain.handle(IPC_CHANNELS.authGetToken, (event): string | null => {
    assertTrustedSender(event, env, IPC_CHANNELS.authGetToken);
    return readToken();
  });

  ipcMain.handle(IPC_CHANNELS.authSetToken, (event, rawToken: unknown) => {
    assertTrustedSender(event, env, IPC_CHANNELS.authSetToken);
    if (typeof rawToken !== "string" || rawToken.length === 0) {
      throw new Error("토큰은 비어 있지 않은 문자열이어야 한다.");
    }
    writeToken(rawToken);
  });

  ipcMain.handle(IPC_CHANNELS.authClearToken, (event) => {
    assertTrustedSender(event, env, IPC_CHANNELS.authClearToken);
    clearToken();
  });

  // 탐색(기획서 6.4). 이벤트는 요청을 낸 WebContents에만 보낸다.
  ipcMain.handle(IPC_CHANNELS.chatAsk, (event, rawInput: unknown): string => {
    assertTrustedSender(event, env, IPC_CHANNELS.chatAsk);
    const input = parseChatAskInput(rawInput);
    const sender = event.sender;

    const handle = coordinator.ask(input, (requestId: string, chatEvent: ChatStreamEvent) => {
      if (sender.isDestroyed()) return;
      sender.send(IPC_CHANNELS.chatEvent, { requestId, event: chatEvent });
    });

    // 창이 닫히면 진행 중 요청을 끊는다. LLM 연결을 붙잡고 있을 이유가 없다
    stopWhenDestroyed(sender, handle);
    return handle.requestId;
  });

  ipcMain.handle(IPC_CHANNELS.chatCancel, (event, rawRequestId: unknown) => {
    assertTrustedSender(event, env, IPC_CHANNELS.chatCancel);
    if (typeof rawRequestId !== "string" || rawRequestId.length === 0) {
      throw new Error("requestId는 비어 있지 않은 문자열이어야 한다.");
    }
    coordinator.cancel(rawRequestId);
  });

  // 사용자 LLM 설정. 키 값은 돌려주지 않고(`hasApiKey`만) 인자도 로그에 남기지 않는다.
  ipcMain.handle(IPC_CHANNELS.llmGetSettings, (event): UserLlmSettings => {
    assertTrustedSender(event, env, IPC_CHANNELS.llmGetSettings);
    return describeLlmSettings();
  });

  ipcMain.handle(IPC_CHANNELS.llmSetSettings, (event, rawInput: unknown) => {
    assertTrustedSender(event, env, IPC_CHANNELS.llmSetSettings);
    const { settings, apiKey } = validateLlmSettingsInput(rawInput);
    writeLlmSettings(settings);
    if (apiKey !== null) writeLlmApiKey(apiKey);
    logger.info("[knot] LLM 설정 저장", { provider: settings.provider, apiKeyChanged: apiKey !== null });
  });

  ipcMain.handle(IPC_CHANNELS.llmClearApiKey, (event) => {
    assertTrustedSender(event, env, IPC_CHANNELS.llmClearApiKey);
    clearLlmApiKey();
    logger.info("[knot] LLM 키 삭제");
  });
}

function stopWhenDestroyed(sender: WebContents, handle: { cancel(): void; done: Promise<void> }): void {
  const onDestroyed = (): void => {
    handle.cancel();
  };
  sender.once("destroyed", onDestroyed);
  void handle.done.then(() => {
    if (!sender.isDestroyed()) sender.removeListener("destroyed", onDestroyed);
  });
}
