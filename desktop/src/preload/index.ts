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
  AgentRegistrationTarget,
  KnotDeepLink,
  KnotDesktopApi,
  LlmStreamEventPayload,
  LlmStreamHandlers,
  LlmStreamInput,
  LlmSubscriptionStatus,
  LoginPromptState,
} from "../shared/api";

type SessionState = "signed-in" | "signed-out";

/** main → renderer 이벤트를 구독한다. `event` 객체는 renderer로 넘기지 않는다 */
function subscribe<T>(channel: string, handler: (payload: T) => void): () => void {
  const listener = (_event: unknown, payload: T): void => {
    handler(payload);
  };
  ipcRenderer.on(channel, listener);
  return () => {
    ipcRenderer.removeListener(channel, listener);
  };
}

/**
 * L2: 질문 하나를 main의 스트림으로 보내고 자기 `requestId`의 이벤트만 콜백에 넘긴다.
 * 끝(complete·error)이나 취소 뒤에는 어떤 콜백도 부르지 않는다. 구독 토큰은 여기로 오지 않는다.
 */
function streamAnswer(input: LlmStreamInput, on: LlmStreamHandlers): () => void {
  const requestId = crypto.randomUUID();
  let finished = false;

  const unsubscribe = subscribe<LlmStreamEventPayload>(IPC_CHANNELS.llmStreamEvent, (payload) => {
    if (finished || payload.requestId !== requestId) return;
    switch (payload.event) {
      case "chunk":
        on.chunk(payload.delta);
        return;
      case "complete":
        finished = true;
        unsubscribe();
        on.complete({ messageId: payload.messageId });
        return;
      case "error":
        finished = true;
        unsubscribe();
        on.error({ code: payload.code, message: payload.message, fallback: payload.fallback });
        return;
      default:
        return;
    }
  });

  ipcRenderer.invoke(IPC_CHANNELS.llmStream, { requestId, ...input }).catch((error: unknown) => {
    // main이 입력을 거부했거나 스트림을 시작하지 못했다. 서버 SSE로 다시 보내면 답은 받을 수 있다
    if (finished) return;
    finished = true;
    unsubscribe();
    on.error({
      code: "LLM_STREAM_FAILED",
      message: error instanceof Error ? error.message : "답변을 시작하지 못했어요",
      fallback: true,
    });
  });

  return () => {
    if (finished) return;
    finished = true;
    unsubscribe();
    ipcRenderer.invoke(IPC_CHANNELS.llmStreamCancel, { requestId }).catch(() => undefined);
  };
}

const api: KnotDesktopApi = {
  version: __KNOT_VERSION__,
  platform: process.platform as KnotDesktopApi["platform"],
  env: __KNOT_ENV__,

  openExternal: (url: string) => ipcRenderer.invoke(IPC_CHANNELS.openExternal, url),

  onDeepLink: (handler: (link: KnotDeepLink) => void) => subscribe<KnotDeepLink>(IPC_CHANNELS.deepLink, handler),

  getPendingDeepLink: () => ipcRenderer.invoke(IPC_CHANNELS.getPendingDeepLink),

  auth: {
    getToken: () => ipcRenderer.invoke(IPC_CHANNELS.authGetToken),
    setToken: (token: string) => ipcRenderer.invoke(IPC_CHANNELS.authSetToken, token),
    clearToken: () => ipcRenderer.invoke(IPC_CHANNELS.authClearToken),
    // 2단계(A7): 메인 창 안 로그인 뷰·기기 세션 폐기·세션 변경 알림(로드맵 Q60·Q68)
    startLogin: () => ipcRenderer.invoke(IPC_CHANNELS.authStartLogin),
    cancelLogin: () => ipcRenderer.invoke(IPC_CHANNELS.authCancelLogin),
    logout: () => ipcRenderer.invoke(IPC_CHANNELS.authLogout),
    onSessionChanged: (handler: (state: SessionState) => void) =>
      subscribe<SessionState>(IPC_CHANNELS.authSessionChanged, handler),
    // 로그인 뷰가 붙어 있는 동안 SPA가 상단 띠에 제목·취소를 그린다(2026-09-10, 기획서 5.2)
    onLoginPromptChanged: (handler: (prompt: LoginPromptState) => void) =>
      subscribe<LoginPromptState>(IPC_CHANNELS.authLoginPrompt, handler),
  },

  // 연결 토큰은 main이 클립보드에만 쓴다. 여기로는 가린 미리보기만 온다(기획서 4.4)
  agent: {
    getStatus: () => ipcRenderer.invoke(IPC_CHANNELS.agentStatus),
    copyRegistration: (target: AgentRegistrationTarget) =>
      ipcRenderer.invoke(IPC_CHANNELS.agentCopyRegistration, target),
    rotateToken: () => ipcRenderer.invoke(IPC_CHANNELS.agentRotateToken),
    setPort: (port: number) => ipcRenderer.invoke(IPC_CHANNELS.agentSetPort, port),
  },

  // L1: 사용자 Claude 구독 로그인. 토큰은 main에만 있고 여기로는 상태만 온다(기획서 6.5)
  llm: {
    getStatus: () => ipcRenderer.invoke(IPC_CHANNELS.llmStatus),
    signIn: () => ipcRenderer.invoke(IPC_CHANNELS.llmSignIn),
    signOut: () => ipcRenderer.invoke(IPC_CHANNELS.llmSignOut),
    onStatusChanged: (handler: (status: LlmSubscriptionStatus) => void) =>
      subscribe<LlmSubscriptionStatus>(IPC_CHANNELS.llmStatusChanged, handler),
    streamAnswer,
    // L3: 설정. 허용 목록은 main이 주고, 변경 값도 main이 다시 검사한다
    getSettings: () => ipcRenderer.invoke(IPC_CHANNELS.llmSettings),
    updateSettings: (input: { model: string; effort: string }) =>
      ipcRenderer.invoke(IPC_CHANNELS.llmUpdateSettings, input),
  },

  // A10: OS 알림. 입력은 main이 다시 검사한다
  notifications: {
    show: (input) => ipcRenderer.invoke(IPC_CHANNELS.notificationsShow, input),
  },
};

contextBridge.exposeInMainWorld(KNOT_DESKTOP_GLOBAL, api);
