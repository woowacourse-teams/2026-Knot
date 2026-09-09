/**
 * Electron main 프로세스 진입점.
 *
 * A1 스파이크 범위(기획서 7절 P0): 원격 오리진 로드, 보안 기본값, Fuses,
 * 네비게이션 허용 목록, 메뉴·외부 링크. 여기에 `S8`(로컬 MCP 서버 — CLI 에이전트 연결)을
 * 얹었다. 트레이·딥링크·자동 업데이트·2단계 인증은 각각 A9·A8·A4·A7에서 붙인다.
 */

import { BrowserWindow, app } from "electron";
import path from "node:path";
import { IPC_CHANNELS } from "../shared/api";
import type { KnotDeepLink } from "../shared/api";
import { resolveEnvironment } from "../shared/env";
import { createAgentBridge } from "./agent/bridge";
import type { AgentBridge } from "./agent/bridge";
import { createToolExecutor } from "./agent/toolExecutor";
import { createKnotApiClient } from "./chat/knotApi";
import { registerIpcHandlers } from "./ipc";
import { initLogging, logger } from "./logging";
import { buildApplicationMenu } from "./menu";
import { applyNavigationPolicy } from "./navigation";
import { readToken } from "./tokenStore";
import { createMainWindow } from "./windows";

const env = resolveEnvironment(__KNOT_ENV__, __KNOT_API_ORIGIN__);

/** `dist/main/index.cjs` 기준. 번들 배치는 `scripts/build.mjs`, 리소스는 앱 루트 `resources/` */
const MCP_ENTRY_PATH = path.join(__dirname, "../mcp/index.cjs");
const SKILL_SOURCE_PATH = path.join(__dirname, "../../resources/skills/knot/SKILL.md");

// 모든 renderer를 sandbox로 강제한다(기획서 8절 #4)
app.enableSandbox();

let mainWindow: BrowserWindow | null = null;
let bridge: AgentBridge | null = null;

function focusMainWindow(): void {
  if (mainWindow === null) return;
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.focus();
}

function openMainWindow(): BrowserWindow {
  const window = createMainWindow(env);
  mainWindow = window;
  window.on("closed", () => {
    if (mainWindow === window) mainWindow = null;
  });
  return window;
}

/**
 * `show_answer`(`S10`, 로드맵 Q51)가 저장을 마친 뒤: 창을 앞으로 가져오고 preload `onDeepLink`로 SPA를
 * 그 세션으로 보낸다. 창이 없으면 새로 열고, 로드 중이면 로드가 끝난 뒤 보낸다. `A8` 딥링크와 같은 채널이다.
 */
function presentAnswer(link: Extract<KnotDeepLink, { type: "chat" }>): void {
  const window = mainWindow ?? openMainWindow();
  const send = (): void => {
    if (!window.isDestroyed()) window.webContents.send(IPC_CHANNELS.deepLink, link);
  };
  if (window.webContents.isLoading()) {
    window.webContents.once("did-finish-load", send);
  } else {
    send();
  }
  focusMainWindow();
  logger.info("[knot] 답변 표시 딥링크", { workspaceId: link.workspaceId, sessionId: link.sessionId });
}

function createBridge(): AgentBridge {
  const api = createKnotApiClient({ apiOrigin: env.apiOrigin, readToken });
  return createAgentBridge({
    userDataDir: app.getPath("userData"),
    version: app.getVersion(),
    mcpEntryPath: MCP_ENTRY_PATH,
    skillSourcePath: SKILL_SOURCE_PATH,
    executor: createToolExecutor(api, { presentAnswer }),
  });
}

function start(): void {
  initLogging(env, app.getVersion());

  // 창이 만들어지는 즉시 정책을 건다. 창 생성 전에 등록해야 첫 창도 덮인다.
  app.on("web-contents-created", (_event, contents) => {
    if (contents.getType() !== "window") return;
    applyNavigationPolicy(contents, env);
  });

  bridge = createBridge();
  registerIpcHandlers(env, bridge);
  buildApplicationMenu(env, () => mainWindow, bridge);
  openMainWindow();
  bridge.start();

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

  app.on("will-quit", () => {
    bridge?.stop();
  });

  app.on("window-all-closed", () => {
    if (process.platform !== "darwin") app.quit();
  });
}
