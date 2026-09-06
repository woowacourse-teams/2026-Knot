/**
 * Electron main 프로세스 진입점.
 *
 * A1 스파이크 범위(기획서 7절 P0): 원격 오리진 로드, 보안 기본값, Fuses,
 * 네비게이션 허용 목록, 메뉴·외부 링크. 트레이·딥링크·자동 업데이트·2단계
 * 인증은 각각 A9·A8·A4·A7에서 붙인다.
 */

import { BrowserWindow, app } from "electron";
import { resolveEnvironment } from "../shared/env";
import { registerIpcHandlers } from "./ipc";
import { initLogging, logger } from "./logging";
import { buildApplicationMenu } from "./menu";
import { applyNavigationPolicy } from "./navigation";
import { createMainWindow } from "./windows";

const env = resolveEnvironment(__KNOT_ENV__, __KNOT_API_ORIGIN__);

// 모든 renderer를 sandbox로 강제한다(기획서 8절 #4)
app.enableSandbox();

let mainWindow: BrowserWindow | null = null;

function focusMainWindow(): void {
  if (mainWindow === null) return;
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.focus();
}

function openMainWindow(): void {
  mainWindow = createMainWindow(env);
  mainWindow.on("closed", () => {
    mainWindow = null;
  });
}

function start(): void {
  initLogging(env, app.getVersion());

  // 창이 만들어지는 즉시 정책을 건다. 창 생성 전에 등록해야 첫 창도 덮인다.
  app.on("web-contents-created", (_event, contents) => {
    if (contents.getType() !== "window") return;
    applyNavigationPolicy(contents, env);
  });

  registerIpcHandlers(env);
  buildApplicationMenu(env, () => mainWindow);
  openMainWindow();

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) openMainWindow();
  });
}

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on("second-instance", () => {
    focusMainWindow();
  });

  app.whenReady().then(start, (error: unknown) => {
    logger.error("[knot] 시작 실패", { error: String(error) });
    app.quit();
  });

  app.on("window-all-closed", () => {
    if (process.platform !== "darwin") app.quit();
  });
}
