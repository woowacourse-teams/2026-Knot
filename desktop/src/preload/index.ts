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
import type { AgentRegistrationTarget, KnotDeepLink, KnotDesktopApi } from "../shared/api";

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
    // 2단계(A7): 시스템 브라우저 로그인·기기 세션 폐기·세션 변경 알림(로드맵 Q60)
    startLogin: () => ipcRenderer.invoke(IPC_CHANNELS.authStartLogin),
    logout: () => ipcRenderer.invoke(IPC_CHANNELS.authLogout),
    onSessionChanged: (handler: (state: SessionState) => void) =>
      subscribe<SessionState>(IPC_CHANNELS.authSessionChanged, handler),
  },

  // 연결 토큰은 main이 클립보드에만 쓴다. 여기로는 가린 미리보기만 온다(기획서 4.4)
  agent: {
    getStatus: () => ipcRenderer.invoke(IPC_CHANNELS.agentStatus),
    copyRegistration: (target: AgentRegistrationTarget) =>
      ipcRenderer.invoke(IPC_CHANNELS.agentCopyRegistration, target),
    rotateToken: () => ipcRenderer.invoke(IPC_CHANNELS.agentRotateToken),
    setPort: (port: number) => ipcRenderer.invoke(IPC_CHANNELS.agentSetPort, port),
  },

  // A10: OS 알림. 입력은 main이 다시 검사한다
  notifications: {
    show: (input) => ipcRenderer.invoke(IPC_CHANNELS.notificationsShow, input),
  },
};

contextBridge.exposeInMainWorld(KNOT_DESKTOP_GLOBAL, api);
