/**
 * preload — renderer에 `window.knotDesktop`을 노출한다.
 *
 * 불변 계약 5: 기획서 4.4 인터페이스만 노출하고 `ipcRenderer` 원본은 넘기지
 * 않는다. `ipcRenderer.on` 콜백의 `event` 객체도 renderer로 전달하지 않는다
 * (`event.sender`로 누출된다).
 *
 * sandbox preload는 ESM을 못 쓰므로 CJS 단일 번들로 빌드한다(지식 §2.2).
 */

import { contextBridge, ipcRenderer } from "electron";
import { IPC_CHANNELS, KNOT_DESKTOP_GLOBAL } from "../shared/api";
import type {
  ChatStreamEvent,
  KnotDeepLink,
  KnotDesktopApi,
  UserLlmSettingsInput,
} from "../shared/api";

/** main이 `knot:chat-event`로 보내는 페이로드(기획서 4.4) */
interface ChatEventPayload {
  requestId: string;
  event: ChatStreamEvent;
}

/**
 * `chat.ask` — invoke로 requestId를 받고, 그 id의 이벤트만 골라 콜백에 넘긴다.
 *
 * 구독은 invoke **전에** 건다. main의 첫 이벤트가 invoke 응답보다 먼저 올 수 있어
 * requestId를 알기 전에 도착한 페이로드는 버퍼에 뒀다가 대조한다. `complete`·`error`가
 * 오면 구독을 끊고, `cancel()` 뒤에는 콜백을 부르지 않는다.
 */
async function askChat(
  input: { sessionId: number; content: string },
  onEvent: (event: ChatStreamEvent) => void,
): Promise<{ cancel(): void }> {
  let requestId: string | null = null;
  let finished = false;
  const beforeId: ChatEventPayload[] = [];

  const unsubscribe = (): void => {
    ipcRenderer.removeListener(IPC_CHANNELS.chatEvent, listener);
  };
  const deliver = (event: ChatStreamEvent): void => {
    if (finished) return;
    if (event.event !== "chunk") {
      finished = true;
      unsubscribe();
    }
    onEvent(event);
  };
  const listener = (_event: unknown, payload: ChatEventPayload): void => {
    if (requestId === null) {
      beforeId.push(payload);
      return;
    }
    if (payload.requestId === requestId) deliver(payload.event);
  };

  ipcRenderer.on(IPC_CHANNELS.chatEvent, listener);
  try {
    // 필요한 두 필드만 넘긴다. main이 다시 검사한다
    requestId = await ipcRenderer.invoke(IPC_CHANNELS.chatAsk, {
      sessionId: input.sessionId,
      content: input.content,
    });
  } catch (error) {
    unsubscribe();
    throw error;
  }

  for (const payload of beforeId.splice(0)) {
    if (payload.requestId === requestId) deliver(payload.event);
  }

  return {
    cancel: () => {
      if (finished) return;
      finished = true;
      unsubscribe();
      void ipcRenderer.invoke(IPC_CHANNELS.chatCancel, requestId).catch(() => undefined);
    },
  };
}

const api: KnotDesktopApi = {
  version: __KNOT_VERSION__,
  platform: process.platform as KnotDesktopApi["platform"],
  env: __KNOT_ENV__,

  openExternal: (url: string) => ipcRenderer.invoke(IPC_CHANNELS.openExternal, url),

  onDeepLink: (handler: (link: KnotDeepLink) => void) => {
    const listener = (_event: unknown, link: KnotDeepLink): void => {
      handler(link);
    };
    ipcRenderer.on(IPC_CHANNELS.deepLink, listener);
    return () => {
      ipcRenderer.removeListener(IPC_CHANNELS.deepLink, listener);
    };
  },

  getPendingDeepLink: () => ipcRenderer.invoke(IPC_CHANNELS.getPendingDeepLink),

  auth: {
    getToken: () => ipcRenderer.invoke(IPC_CHANNELS.authGetToken),
    setToken: (token: string) => ipcRenderer.invoke(IPC_CHANNELS.authSetToken, token),
    clearToken: () => ipcRenderer.invoke(IPC_CHANNELS.authClearToken),
  },

  chat: {
    ask: askChat,
  },

  llm: {
    getSettings: () => ipcRenderer.invoke(IPC_CHANNELS.llmGetSettings),
    setSettings: (input: UserLlmSettingsInput) =>
      ipcRenderer.invoke(IPC_CHANNELS.llmSetSettings, {
        provider: input.provider,
        baseUrl: input.baseUrl,
        model: input.model,
        ...(input.apiKey === undefined ? {} : { apiKey: input.apiKey }),
      }),
    clearApiKey: () => ipcRenderer.invoke(IPC_CHANNELS.llmClearApiKey),
  },
};

contextBridge.exposeInMainWorld(KNOT_DESKTOP_GLOBAL, api);
