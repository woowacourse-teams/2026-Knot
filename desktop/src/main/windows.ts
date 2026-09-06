/**
 * 메인 창 생성.
 *
 * 보안 기본값은 기획서 8절 #1~#6, #18을 따른다. `nodeIntegration`·
 * `contextIsolation`·`sandbox`는 협상 대상이 아니다(불변 계약 4).
 */

import { BrowserWindow, session } from "electron";
import path from "node:path";
import type { KnotEnvironment } from "../shared/env";
import { logger } from "./logging";
import { applySessionPolicy } from "./navigation";

/** 웹 세션을 디스크에 영속시키는 파티션. 재실행해도 로그인 쿠키가 남는다(기획서 5.1) */
const SESSION_PARTITION = "persist:knot";

/** `did-fail-load`의 ERR_ABORTED. 사용자가 이동을 취소한 경우라 오류 화면을 띄우지 않는다 */
const ERR_ABORTED = -3;

const PRELOAD_PATH = path.join(__dirname, "../preload/index.cjs");
const OFFLINE_PAGE_PATH = path.join(__dirname, "../../resources/offline.html");

export function createMainWindow(env: KnotEnvironment): BrowserWindow {
  const knotSession = session.fromPartition(SESSION_PARTITION);
  applySessionPolicy(knotSession, env);

  const window = new BrowserWindow({
    width: 1280,
    height: 832,
    minWidth: 960,
    minHeight: 600,
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
    window.show();
  });

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
