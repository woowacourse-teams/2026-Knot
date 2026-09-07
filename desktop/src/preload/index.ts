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
import type { KnotDeepLink, KnotDesktopApi } from "../shared/api";

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
};

contextBridge.exposeInMainWorld(KNOT_DESKTOP_GLOBAL, api);
