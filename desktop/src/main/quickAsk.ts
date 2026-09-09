/**
 * 퀵 질문 창 + 마지막 워크스페이스 추적 (기획서 7절 P2, 로드맵 A9).
 *
 * 글로벌 단축키·트레이에서 여는 작은 `BrowserWindow`에 웹 SPA의 채팅 화면
 * `/workspace/:id/chat`을 그대로 로드한다. 데스크톱 전용 화면은 만들지 않는다(기획서 2.2).
 * 메인 창과 같은 세션 파티션·preload·보안 webPreferences를 쓰므로(불변 계약 4) SPA는
 * 이 창에서도 `window.knotDesktop`으로 토큰 저장소를 쓴다. 네비게이션 정책은
 * `index.ts`의 `web-contents-created`가 이 창에도 건다.
 *
 * 착수 가정(로드맵 4.1절 A9):
 * - 어느 워크스페이스의 채팅을 열지는 main이 **메인 창의 URL**에서 본 마지막 `/workspace/:id`로 정한다
 *   (`userData/last-workspace.json`에 남겨 재시작 뒤에도 쓴다). 본 적이 없으면 워크스페이스 목록
 *   `/workspace`를 연다. 서버에 목록을 물어 첫 워크스페이스를 고르는 방식은 사용자의 의도와
 *   어긋날 수 있어 쓰지 않는다.
 * - 단축키는 `CommandOrControl+Shift+K`. 창을 닫으면 숨기기만 하고 다음에 즉시 다시 띄운다.
 *   `Escape`도 숨기기다. 앱 종료(`before-quit`) 때만 실제로 닫는다.
 */

import { BrowserWindow, app, session } from "electron";
import type { WebContents } from "electron";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import type { KnotEnvironment } from "../shared/env";
import { toOrigin } from "../shared/env";
import { logger } from "./logging";

export const QUICK_ASK_ACCELERATOR = "CommandOrControl+Shift+K";
export const LAST_WORKSPACE_FILE_NAME = "last-workspace.json";

/** `windows.ts`와 같은 값. 퀵 질문 창은 메인 창의 로그인 상태를 그대로 써야 한다 */
const SESSION_PARTITION = "persist:knot";
const PRELOAD_PATH = path.join(__dirname, "../preload/index.cjs");
const WORKSPACE_ID_PATTERN = /^[1-9]\d{0,17}$/;

/** 웹 오리진의 `/workspace/:id…` URL에서 워크스페이스 ID를 뽑는다. 아니면 null */
export function extractWorkspaceId(rawUrl: string, webOrigin: string): string | null {
  if (toOrigin(rawUrl) !== webOrigin) return null;
  let pathname: string;
  try {
    pathname = new URL(rawUrl).pathname;
  } catch {
    return null;
  }
  const [, head, id] = pathname.split("/");
  if (head !== "workspace" || id === undefined || !WORKSPACE_ID_PATTERN.test(id)) return null;
  return id;
}

/** 퀵 질문 창이 로드할 URL. 워크스페이스를 모르면 목록 화면 */
export function quickAskUrl(webOrigin: string, workspaceId: string | null): string {
  return workspaceId === null ? `${webOrigin}/workspace` : `${webOrigin}/workspace/${workspaceId}/chat`;
}

export interface LastWorkspaceStore {
  read(): string | null;
  write(workspaceId: string): void;
}

export function createLastWorkspaceStore(userDataDir: string): LastWorkspaceStore {
  const filePath = path.join(userDataDir, LAST_WORKSPACE_FILE_NAME);
  let cached: string | null | undefined;

  return {
    read() {
      if (cached !== undefined) return cached;
      try {
        const parsed = JSON.parse(readFileSync(filePath, "utf8")) as { workspaceId?: unknown };
        cached =
          typeof parsed.workspaceId === "string" && WORKSPACE_ID_PATTERN.test(parsed.workspaceId)
            ? parsed.workspaceId
            : null;
      } catch {
        cached = null;
      }
      return cached;
    },
    write(workspaceId) {
      if (!WORKSPACE_ID_PATTERN.test(workspaceId) || workspaceId === cached) return;
      cached = workspaceId;
      try {
        mkdirSync(userDataDir, { recursive: true });
        writeFileSync(filePath, JSON.stringify({ workspaceId }) + "\n");
      } catch (error) {
        logger.warn("[knot] 마지막 워크스페이스 저장 실패", { reason: String(error) });
      }
    },
  };
}

/** 창의 네비게이션을 보고 마지막 워크스페이스를 기억한다. SPA 라우터 이동(`did-navigate-in-page`)도 본다 */
export function trackLastWorkspace(contents: WebContents, env: KnotEnvironment, store: LastWorkspaceStore): void {
  const remember = (url: string): void => {
    const workspaceId = extractWorkspaceId(url, env.webOrigin);
    if (workspaceId !== null) store.write(workspaceId);
  };
  contents.on("did-navigate", (_event, url) => {
    remember(url);
  });
  contents.on("did-navigate-in-page", (_event, url, isMainFrame) => {
    if (isMainFrame) remember(url);
  });
}

export interface QuickAskWindow {
  /** 보이면 숨기고, 아니면 앞으로 가져온다(단축키·트레이) */
  toggle(): void;
  show(): void;
  /** 현재 창 객체. 없으면 null(테스트·상태 확인용) */
  current(): BrowserWindow | null;
}

export function createQuickAskWindow(env: KnotEnvironment, store: LastWorkspaceStore): QuickAskWindow {
  let window: BrowserWindow | null = null;
  let loadedWorkspaceId: string | null | undefined;
  let quitting = false;

  app.on("before-quit", () => {
    quitting = true;
  });

  function create(): BrowserWindow {
    const knotSession = session.fromPartition(SESSION_PARTITION);
    const created = new BrowserWindow({
      width: 480,
      height: 680,
      minWidth: 360,
      minHeight: 480,
      show: false,
      alwaysOnTop: true,
      title: "Knot 퀵 질문",
      backgroundColor: "#ffffff",
      webPreferences: {
        preload: PRELOAD_PATH,
        session: knotSession,
        sandbox: true,
        contextIsolation: true,
        nodeIntegration: false,
        webviewTag: false,
      },
    });

    created.on("close", (event) => {
      if (quitting) return;
      event.preventDefault();
      created.hide();
    });
    created.on("closed", () => {
      window = null;
      loadedWorkspaceId = undefined;
    });
    created.webContents.on("before-input-event", (_event, input) => {
      if (input.type === "keyDown" && input.key === "Escape") created.hide();
    });
    created.webContents.on("did-fail-load", (_event, errorCode, errorDescription, validatedUrl, isMainFrame) => {
      if (!isMainFrame) return;
      logger.warn("[knot] 퀵 질문 창 로드 실패", { errorCode, errorDescription, url: validatedUrl });
    });
    return created;
  }

  function show(): void {
    if (window === null) window = create();
    const workspaceId = store.read();
    if (loadedWorkspaceId !== workspaceId) {
      loadedWorkspaceId = workspaceId;
      const url = quickAskUrl(env.webOrigin, workspaceId);
      logger.info("[knot] 퀵 질문 창 로드", { workspaceId });
      void window.loadURL(url);
    }
    window.show();
    window.focus();
  }

  return {
    toggle() {
      if (window !== null && window.isVisible() && window.isFocused()) {
        window.hide();
        return;
      }
      show();
    },
    show,
    current() {
      return window;
    },
  };
}
