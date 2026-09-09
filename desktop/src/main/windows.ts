/**
 * 메인 창 생성·상태 복원.
 *
 * 보안 기본값은 기획서 8절 #1~#6, #18을 따른다. `nodeIntegration`·
 * `contextIsolation`·`sandbox`는 협상 대상이 아니다(불변 계약 4).
 * 창 크기·위치는 `A2`(로드맵 4.1절 착수 가정)대로 `window-state.json`에서 되살리고 닫을 때 저장한다.
 */

import { BrowserWindow, screen } from "electron";
import type { Session } from "electron";
import path from "node:path";
import type { KnotEnvironment } from "../shared/env";
import { logger } from "./logging";
import { MIN_WINDOW_HEIGHT, MIN_WINDOW_WIDTH, resolveWindowState } from "./windowState";
import type { WindowStateStore } from "./windowState";

/** `did-fail-load`의 ERR_ABORTED. 사용자가 이동을 취소한 경우라 오류 화면을 띄우지 않는다 */
const ERR_ABORTED = -3;

const PRELOAD_PATH = path.join(__dirname, "../preload/index.cjs");
const OFFLINE_PAGE_PATH = path.join(__dirname, "../../resources/offline.html");

export interface MainWindowOptions {
  /** 웹 세션(`persist:knot`). 권한·Bearer 주입·동기화 감시는 호출자가 이 세션에 미리 건다 */
  session: Session;
  /** 없으면 기본 크기로 띄우고 저장하지 않는다(테스트·일회성 실행) */
  stateStore?: WindowStateStore;
}

export function createMainWindow(env: KnotEnvironment, options: MainWindowOptions): BrowserWindow {
  const { session: knotSession, stateStore } = options;
  const state = resolveWindowState(
    stateStore?.read() ?? null,
    screen.getAllDisplays().map((display) => display.workArea),
  );

  const window = new BrowserWindow({
    x: state.x,
    y: state.y,
    width: state.width,
    height: state.height,
    minWidth: MIN_WINDOW_WIDTH,
    minHeight: MIN_WINDOW_HEIGHT,
    show: false,
    backgroundColor: "#ffffff",
    title: "Knot",
    webPreferences: {
      preload: PRELOAD_PATH,
      session: knotSession,
      sandbox: true,
      contextIsolation: true,
      nodeIntegration: false,
      webviewTag: false,
    },
  });

  window.once("ready-to-show", () => {
    if (state.isMaximized) window.maximize();
    window.show();
  });

  if (stateStore !== undefined) {
    // `close`는 창이 파괴되기 전이라 크기를 읽을 수 있다. 최대화 상태면 일반 크기를 따로 남긴다
    window.on("close", () => {
      const bounds = window.getNormalBounds();
      stateStore.write({ ...bounds, isMaximized: window.isMaximized() });
    });
  }

  window.webContents.on("did-fail-load", (_event, errorCode, errorDescription, validatedUrl, isMainFrame) => {
    if (!isMainFrame || errorCode === ERR_ABORTED) return;
    logger.error("[knot] 로드 실패", { errorCode, errorDescription, url: validatedUrl });
    void window.loadFile(OFFLINE_PAGE_PATH);
  });

  window.webContents.on("render-process-gone", (_event, details) => {
    logger.error("[knot] renderer 종료", details);
  });

  logger.info("[knot] 웹 오리진 로드", { url: env.webOrigin });
  void window.loadURL(env.webOrigin);

  return window;
}

/** 오류 화면에서 다시 시도할 때 쓴다. 메뉴의 "Knot 다시 열기" */
export function reloadWebOrigin(window: BrowserWindow, env: KnotEnvironment): void {
  logger.info("[knot] 웹 오리진 재로드", { url: env.webOrigin });
  void window.loadURL(env.webOrigin);
}
