/**
 * Electron main 프로세스 진입점.
 *
 * `A1` 셸(원격 오리진 로드·보안 기본값·네비게이션 허용 목록·메뉴) 위에 다음을 배선한다.
 * - `S8`·`S10` 로컬 MCP 서버(CLI 에이전트 연결)와 `show_answer`
 * - `A2` 창 상태 복원, `A4` 자동 업데이트(Q59 정책), `A7` 시스템 브라우저 로그인·토큰 갱신·Bearer 주입(Q55),
 *   `A8` `knot://` 딥링크, `A9` 트레이·글로벌 단축키·퀵 질문 창, `A10` Notion 동기화 알림·Dock 배지
 * - `L1` 사용자 Claude 구독 로그인·토큰 갱신(기획서 6.5)
 * 순수 로직은 각 모듈에 있고 여기서는 Electron 생명주기에 잇기만 한다(로드맵 4.1절 착수 가정).
 */

import { BrowserWindow, app, dialog, session } from "electron";
import path from "node:path";
import type { KnotDeepLink } from "../shared/api";
import { resolveEnvironment } from "../shared/env";
import { createAgentBridge } from "./agent/bridge";
import type { AgentBridge } from "./agent/bridge";
import { createToolExecutor } from "./agent/toolExecutor";
import { attachBearerInjector } from "./auth/bearerInjector";
import { createDesktopAuth } from "./auth/desktopAuth";
import type { AuthController } from "./auth/loginFlow";
import { createKnotApiClient } from "./chat/knotApi";
import {
  attachDeepLinkHandling,
  createDeepLinkRouter,
  focusWindow,
  handleColdStartDeepLink,
  registerProtocolClient,
} from "./deepLink";
import type { DeepLinkRouter } from "./deepLink";
import { registerIpcHandlers } from "./ipc";
import { createDesktopLlm } from "./llm/desktopLlm";
import type { DesktopLlm } from "./llm/desktopLlm";
import { initLogging, logger } from "./logging";
import { buildApplicationMenu } from "./menu";
import { applyNavigationPolicy, applySessionPolicy } from "./navigation";
import {
  attachSyncWatcher,
  buildSyncNotification,
  createDockBadge,
  createSyncWatcher,
  showDesktopNotification,
} from "./notifications";
import type { DesktopNotificationInput, SyncWatcher } from "./notifications";
import { QUICK_ASK_ACCELERATOR, createLastWorkspaceStore, createQuickAskWindow, trackLastWorkspace } from "./quickAsk";
import type { QuickAskWindow } from "./quickAsk";
import { SESSION_PARTITION } from "./sessionPartition";
import { readToken } from "./tokenStore";
import { createTray, registerQuickAskShortcut, unregisterAllShortcuts } from "./tray";
import { checkForUpdatesNow, initAutoUpdate } from "./updater";
import { createWindowStateStore } from "./windowState";
import { createMainWindow } from "./windows";

const env = resolveEnvironment(__KNOT_ENV__, __KNOT_API_ORIGIN__);

/** `dist/main/index.cjs` 기준. 번들 배치는 `scripts/build.mjs`, 리소스는 앱 루트 `resources/` */
const MCP_ENTRY_PATH = path.join(__dirname, "../mcp/index.cjs");
const SKILL_SOURCE_PATH = path.join(__dirname, "../../resources/skills/knot/SKILL.md");
const TRAY_ICON_PATH = path.join(__dirname, "../../resources/tray/knotTemplate.png");

// 모든 renderer를 sandbox로 강제한다(기획서 8절 #4)
app.enableSandbox();

let mainWindow: BrowserWindow | null = null;
let bridge: AgentBridge | null = null;
let auth: AuthController | null = null;
let llm: DesktopLlm | null = null;
let syncWatcher: SyncWatcher | null = null;
let quickAsk: QuickAskWindow | null = null;

const getMainWindow = (): BrowserWindow | null => mainWindow;

/** 창이 있으면 앞으로, 없으면 새로 연다(트레이·Dock·딥링크 공통) */
function openOrFocusMainWindow(): BrowserWindow {
  if (mainWindow !== null && !mainWindow.isDestroyed()) {
    focusWindow(mainWindow);
    return mainWindow;
  }
  return openMainWindow();
}

// A8: `open-url`은 `ready` 전에도 발화하므로 라우터를 모듈 로드 시점에 만들어 잇는다
const deepLinks: DeepLinkRouter = createDeepLinkRouter({
  getWindow: getMainWindow,
  openWindow: () => openMainWindow(),
});
attachDeepLinkHandling(deepLinks);

/** A10: 알림을 누르면 딥링크로 이동하고, 링크가 없으면 창만 앞으로 */
function onNotificationClick(link: KnotDeepLink | null): void {
  if (link === null) {
    openOrFocusMainWindow();
    return;
  }
  deepLinks.dispatch(link);
}

function showNotification(input: DesktopNotificationInput): void {
  showDesktopNotification(input, onNotificationClick);
}

const lastWorkspace = createLastWorkspaceStore(app.getPath("userData"));
const windowState = createWindowStateStore(app.getPath("userData"));

function openMainWindow(): BrowserWindow {
  const knotSession = session.fromPartition(SESSION_PARTITION);
  const window = createMainWindow(env, { session: knotSession, stateStore: windowState });
  mainWindow = window;
  // A9: 퀵 질문 창이 열 워크스페이스를 메인 창의 URL에서 기억한다
  trackLastWorkspace(window.webContents, env, lastWorkspace);
  window.on("closed", () => {
    if (mainWindow === window) mainWindow = null;
  });
  return window;
}

/**
 * `show_answer`(`S10`, 로드맵 Q51)가 저장을 마친 뒤: 창을 앞으로 가져오고 preload `onDeepLink`로 SPA를
 * 그 세션으로 보낸다. `A8` 딥링크 라우터와 같은 경로라 창이 없거나 로드 중인 경우도 같은 규칙을 따른다.
 */
function presentAnswer(link: Extract<KnotDeepLink, { type: "chat" }>): void {
  deepLinks.dispatch(link);
  logger.info("[knot] 답변 표시 딥링크", { workspaceId: link.workspaceId, sessionId: link.sessionId });
}

/** Bearer 서버 API 클라이언트. `S8` 브리지(도구 실행)와 `L2` 앱 안 답변이 함께 쓴다 */
const knotApi = createKnotApiClient({ apiOrigin: env.apiOrigin, readToken });

function createBridge(): AgentBridge {
  const api = knotApi;
  return createAgentBridge({
    userDataDir: app.getPath("userData"),
    version: app.getVersion(),
    mcpEntryPath: MCP_ENTRY_PATH,
    skillSourcePath: SKILL_SOURCE_PATH,
    executor: createToolExecutor(api, { presentAnswer }),
  });
}

/** A10: 동기화 완료·실패를 OS 알림 + Dock 배지로 */
function createSyncNotifier(): SyncWatcher {
  const badge = createDockBadge();
  return createSyncWatcher({
    apiOrigin: env.apiOrigin,
    readToken,
    onNotice: (notice) => {
      const shown = showDesktopNotification(buildSyncNotification(notice), onNotificationClick);
      const focused = BrowserWindow.getFocusedWindow() !== null;
      if (shown && !focused) badge.bump();
    },
  });
}

/** 웹 세션(`persist:knot`)에 한 번만 거는 정책·감시. 창을 다시 열어도 세션은 같다 */
function prepareWebSession(): void {
  const knotSession = session.fromPartition(SESSION_PARTITION);
  applySessionPolicy(knotSession, env);
  // A7(Q55): API 오리진 XHR·fetch에 저장된 액세스 토큰을 주입한다. 이미 헤더가 있으면 손대지 않는다
  attachBearerInjector(knotSession, env.apiOrigin, readToken);
  syncWatcher = createSyncNotifier();
  attachSyncWatcher(knotSession, syncWatcher, env.apiOrigin);
}

/** A9: 트레이·글로벌 단축키. 단축키를 못 잡아도 메뉴·트레이로 연다 */
function setupTrayAndShortcut(): void {
  const quickAskWindow = createQuickAskWindow(env, lastWorkspace);
  quickAsk = quickAskWindow;
  createTray(TRAY_ICON_PATH, {
    onOpen: () => {
      openOrFocusMainWindow();
    },
    onQuickAsk: () => quickAskWindow.toggle(),
    onQuit: () => app.quit(),
    quickAskAccelerator: QUICK_ASK_ACCELERATOR,
  });
  registerQuickAskShortcut(QUICK_ASK_ACCELERATOR, () => quickAskWindow.toggle());
}

/** 메뉴 "업데이트 확인". 정책상 꺼진 빌드에서는 이유를 알려 준다(Q59) */
function checkForUpdatesFromMenu(): void {
  if (checkForUpdatesNow()) return;
  void dialog.showMessageBox({
    type: "info",
    title: "Knot",
    message: "이 빌드는 자동 업데이트 대상이 아니에요.",
    detail: "서명된 정식(prod) 배포본에서만 업데이트를 확인해요.",
  });
}

function start(): void {
  initLogging(env, app.getVersion());
  registerProtocolClient();

  // 창이 만들어지는 즉시 정책을 건다. 창 생성 전에 등록해야 첫 창도 덮인다.
  app.on("web-contents-created", (_event, contents) => {
    if (contents.getType() !== "window") return;
    applyNavigationPolicy(contents, env);
  });

  prepareWebSession();
  const desktopAuth = createDesktopAuth(env, { getWindow: getMainWindow });
  auth = desktopAuth;
  // A7 2차 경로: `knot://auth/callback`이 오면 대기 중인 로그인에 넘긴다
  deepLinks.onAuthCallback((callback) => {
    desktopAuth.handleCallback({
      ...(callback.code === null ? {} : { code: callback.code }),
      ...(callback.state === null ? {} : { state: callback.state }),
      ...(callback.error === null ? {} : { error: callback.error }),
    });
  });

  // L1·L2: 사용자 Claude 구독 로그인·앱 안 답변. 토큰은 main의 safeStorage(`subscription-auth.bin`)에만 둔다
  const desktopLlm = createDesktopLlm({ userDataDir: app.getPath("userData"), api: knotApi });
  llm = desktopLlm;

  bridge = createBridge();
  registerIpcHandlers(env, { bridge, deepLinks, auth: desktopAuth, showNotification, llm: desktopLlm });
  buildApplicationMenu({
    env,
    getWindow: getMainWindow,
    bridge,
    auth: desktopAuth,
    llm: desktopLlm,
    checkForUpdates: checkForUpdatesFromMenu,
  });
  setupTrayAndShortcut();

  // A8: Windows·Linux 콜드 스타트 인자. 링크가 있으면 라우터가 창을 연다
  handleColdStartDeepLink(deepLinks);
  if (mainWindow === null) openMainWindow();

  bridge.start();
  desktopAuth.restore();
  desktopLlm.restore();
  initAutoUpdate(env.name);

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) openMainWindow();
  });
}

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on("second-instance", () => {
    // 딥링크 인자는 `attachDeepLinkHandling`이 같은 이벤트에서 처리한다
    if (mainWindow !== null) focusWindow(mainWindow);
  });

  app.whenReady().then(start, (error: unknown) => {
    logger.error("[knot] 시작 실패", { error: String(error) });
    app.quit();
  });

  app.on("will-quit", () => {
    unregisterAllShortcuts();
    syncWatcher?.stop();
    auth?.dispose();
    llm?.dispose();
    bridge?.stop();
  });

  // macOS는 창을 모두 닫아도 트레이·Dock으로 남는다(A9 상시 실행). 다른 OS는 종료
  app.on("window-all-closed", () => {
    if (process.platform !== "darwin") app.quit();
  });
}

export { quickAsk };
