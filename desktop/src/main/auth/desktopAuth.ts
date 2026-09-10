/**
 * 메인 창 안 로그인 뷰 — Electron 접착층 (기획서 5.2, 로드맵 `A7`·Q68).
 *
 * `loginFlow`의 상태 기계에 실제 의존성을 꽂는다: 로그인 뷰(`loginView`, API 오리진 인가 URL만),
 * loopback 서버, 토큰 저장소(`auth.bin`·`auth-session.bin`), 기기 정보. 세션이 바뀌면 열린 창 전부에
 * `knot:auth-session-changed`를 보내고, 메인 창을 웹 오리진(로그인 뒤) 또는 `/login`(로그아웃 뒤)으로
 * 옮긴다. 로그아웃은 파티션의 쿠키(GitHub·Notion OAuth 세션)도 지운다(지식 §4.8).
 * 토큰 값은 IPC 인자·로그 어디에도 싣지 않는다.
 */

import { BrowserWindow, app, session } from "electron";
import { hostname } from "node:os";
import { IPC_CHANNELS } from "../../shared/api";
import type { KnotEnvironment } from "../../shared/env";
import { toOrigin } from "../../shared/env";
import { logger } from "../logging";
import { SESSION_PARTITION } from "../sessionPartition";
import { clearToken, readToken, writeToken } from "../tokenStore";
import { createDeviceSessionStore } from "./deviceSessionStore";
import { createDeviceTokenApi } from "./deviceTokenApi";
import { createAuthController } from "./loginFlow";
import type { AuthController, SessionState } from "./loginFlow";
import { LOGIN_HEADER_HEIGHT, openLoginView } from "./loginView";
import { startLoopbackServer } from "./loopbackServer";

export interface DesktopAuthOptions {
  getWindow(): BrowserWindow | null;
}

export function createDesktopAuth(env: KnotEnvironment, options: DesktopAuthOptions): AuthController {
  /**
   * 로그인 뷰가 붙고 떨어질 때 메인 창의 SPA에 알린다(기획서 5.2, 2026-09-10).
   *
   * SPA는 열려 있는 동안 뷰가 덮지 않는 상단 띠에 제목과 "취소"를 그린다. 그리지 않아도
   * 뷰 안 `Esc`로 빠져나올 수 있다(로드맵 R31).
   */
  function notifyPrompt(window: BrowserWindow, open: boolean): void {
    if (window.isDestroyed()) return;
    window.webContents.send(IPC_CHANNELS.authLoginPrompt, {
      open,
      headerHeight: open ? LOGIN_HEADER_HEIGHT : 0,
    });
  }

  function broadcast(state: SessionState): void {
    for (const window of BrowserWindow.getAllWindows()) {
      if (window.isDestroyed()) continue;
      window.webContents.send(IPC_CHANNELS.authSessionChanged, state);
    }
  }

  function navigateMainWindow(url: string): void {
    const window = options.getWindow();
    if (window === null || window.isDestroyed()) return;
    void window.loadURL(url);
    if (window.isMinimized()) window.restore();
    window.focus();
  }

  function onSessionChanged(state: SessionState): void {
    logger.info("[knot] 세션 상태 변경", { state });
    if (state === "signed-out") {
      session
        .fromPartition(SESSION_PARTITION)
        .clearStorageData({ storages: ["cookies"] })
        .catch((error: unknown) => {
          logger.warn("[knot] 쿠키 삭제 실패", { reason: error instanceof Error ? error.name : "Unknown" });
        })
        .finally(() => {
          broadcast(state);
          navigateMainWindow(`${env.webOrigin}/login`);
        });
      return;
    }
    broadcast(state);
    navigateMainWindow(env.webOrigin);
  }

  return createAuthController({
    apiOrigin: env.apiOrigin,
    api: createDeviceTokenApi({ apiOrigin: env.apiOrigin }),
    accessTokens: { read: readToken, write: writeToken, clear: clearToken },
    sessions: createDeviceSessionStore(),
    startLoopback: startLoopbackServer,
    openLoginPage: ({ url, callbackOrigin, onCancelled }) => {
      // 인가 URL은 빌드 시 고정된 API 오리진으로 직접 조립한 값이다. 그 밖의 오리진은 열지 않는다
      if (toOrigin(url) !== env.apiOrigin) {
        return Promise.reject(new Error("API 오리진의 인가 URL만 로그인 뷰에서 연다."));
      }
      const window = options.getWindow();
      if (window === null || window.isDestroyed()) {
        // 붙일 메인 창이 없으면 로그인을 시작하지 않는다. 창을 새로 만들지 않는다(로드맵 Q68)
        return Promise.reject(new Error("로그인 뷰를 붙일 메인 창이 없다."));
      }
      const view = openLoginView({
        url,
        callbackOrigin,
        window,
        session: session.fromPartition(SESSION_PARTITION),
        env,
        onCancelled,
      });
      notifyPrompt(window, true);
      return Promise.resolve({
        focus: () => {
          view.focus();
        },
        close: () => {
          view.close();
          notifyPrompt(window, false);
        },
      });
    },
    device: () => ({ name: hostname(), platform: process.platform, appVersion: app.getVersion() }),
    onSessionChanged,
  });
}
