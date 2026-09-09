/**
 * `knot://` 딥링크 (기획서 4.3·4.4, 로드맵 A8·Q7).
 *
 * 문법(정본은 기획서 4.3):
 * - `knot://invite/<token>`                → `{type: "invite", token}`
 * - `knot://chat/<workspaceId>[/<sessionId>]` → `{type: "chat", workspaceId, sessionId?}`
 * - `knot://auth/callback?code&state|error` → main에서만 소비(A7 시스템 브라우저 로그인 콜백).
 *   renderer로 보내지 않는다(기획서 4.4 규칙 "`auth.callback` 같은 로그인 콜백 데이터는 main에서만 소비").
 *
 * 파서는 순수 함수라 vitest로 검증한다. 수신 경로는 macOS `open-url`, Windows·Linux는
 * `second-instance`의 argv와 콜드 스타트 `process.argv`다. 앱이 꺼져 있을 때(콜드 스타트) 들어온
 * 링크는 보관했다가 SPA가 `getPendingDeepLink()`로 한 번 가져간다.
 *
 * 착수 가정(로드맵 4.1절 A8):
 * - 웜 스타트(창이 로드된 상태)는 `knot:deep-link` 이벤트로 보내고, SPA 리스너가 아직 없을 때를 위해
 *   같은 링크를 60초 동안 보류 목록에도 둔다. 콜드 스타트는 보류만 하고 이벤트를 보내지 않는다
 *   (SPA가 부팅하면서 `getPendingDeepLink()`를 부르므로 두 번 이동하지 않는다).
 * - 보류 링크는 60초가 지나면 버린다. 부팅이 60초를 넘길 일이 없고, 오래된 링크로 엉뚱한 화면에
 *   가는 것이 더 나쁘다.
 * - 토큰·ID는 문법 검사만 한다(초대 토큰 `[A-Za-z0-9_-]` 1~255자, ID는 양의 정수). 존재 여부는 SPA·서버가 판단한다.
 * - 스킴 등록(`app.setAsDefaultProtocolClient`)은 패키징 앱에서만 실효가 있다. 미패키징 macOS는
 *   Info.plist가 없어 등록이 실패하며 로그로만 남긴다(로드맵 G4는 패키징 앱에서 실측).
 */

import { app } from "electron";
import type { BrowserWindow } from "electron";
import { IPC_CHANNELS } from "../shared/api";
import type { KnotDeepLink } from "../shared/api";
import { logger } from "./logging";

/** 로드맵 Q7 기본값. 충돌이 실측되면 reverse-domain으로 바꾼다 */
export const DEEP_LINK_SCHEME = "knot";

/** 보류 링크 유효 시간. 콜드 스타트 부팅이 이 안에 끝나야 한다 */
export const PENDING_DEEP_LINK_TTL_MS = 60_000;

/** A7이 소비하는 로그인 콜백. renderer로 나가지 않는다 */
export interface AuthCallbackDeepLink {
  type: "auth";
  code: string | null;
  state: string | null;
  error: string | null;
}

export type DeepLinkTarget = KnotDeepLink | AuthCallbackDeepLink;

const INVITE_TOKEN_PATTERN = /^[A-Za-z0-9_-]{1,255}$/;
const ID_PATTERN = /^[1-9]\d{0,17}$/;
const MAX_URL_LENGTH = 2_048;

/** `knot://…` 문자열을 목적지로 바꾼다. 문법에 맞지 않으면 null */
export function parseDeepLink(rawUrl: string): DeepLinkTarget | null {
  if (typeof rawUrl !== "string" || rawUrl.length === 0 || rawUrl.length > MAX_URL_LENGTH) return null;

  let url: URL;
  try {
    url = new URL(rawUrl);
  } catch {
    return null;
  }
  if (url.protocol !== `${DEEP_LINK_SCHEME}:`) return null;
  if (url.username !== "" || url.password !== "") return null;

  // `knot://invite/x`는 host=invite, `knot:///invite/x`는 host="" pathname=/invite/x — 둘 다 받는다
  const segments = [url.hostname, ...url.pathname.split("/")]
    .map((segment) => decodeURIComponentSafe(segment))
    .filter((segment): segment is string => segment !== null && segment.length > 0);

  const [head, ...rest] = segments;
  switch (head) {
    case "invite": {
      const [token] = rest;
      if (rest.length !== 1 || token === undefined || !INVITE_TOKEN_PATTERN.test(token)) return null;
      return { type: "invite", token };
    }
    case "chat": {
      const [workspaceId, sessionId] = rest;
      if (rest.length < 1 || rest.length > 2) return null;
      if (workspaceId === undefined || !ID_PATTERN.test(workspaceId)) return null;
      if (sessionId === undefined) return { type: "chat", workspaceId };
      if (!ID_PATTERN.test(sessionId)) return null;
      return { type: "chat", workspaceId, sessionId };
    }
    case "auth": {
      if (rest.length !== 1 || rest[0] !== "callback") return null;
      return {
        type: "auth",
        code: url.searchParams.get("code"),
        state: url.searchParams.get("state"),
        error: url.searchParams.get("error"),
      };
    }
    default:
      return null;
  }
}

/** renderer·IPC에서 받은 값이 `KnotDeepLink` 모양인지 검사한다. 값은 믿지 않는다 */
export function isKnotDeepLink(value: unknown): value is KnotDeepLink {
  if (typeof value !== "object" || value === null) return false;
  const { type, token, workspaceId, sessionId } = value as Record<string, unknown>;
  if (type === "invite") {
    return typeof token === "string" && INVITE_TOKEN_PATTERN.test(token);
  }
  if (type === "chat") {
    if (typeof workspaceId !== "string" || !ID_PATTERN.test(workspaceId)) return false;
    return sessionId === undefined || (typeof sessionId === "string" && ID_PATTERN.test(sessionId));
  }
  return false;
}

function decodeURIComponentSafe(value: string): string | null {
  try {
    return decodeURIComponent(value);
  } catch {
    return null;
  }
}

/** Windows·Linux는 딥링크가 실행 인자로 온다. 마지막 `knot://` 인자를 고른다 */
export function findDeepLinkArg(argv: readonly string[]): string | null {
  for (let index = argv.length - 1; index >= 0; index -= 1) {
    const arg = argv[index];
    if (arg !== undefined && arg.startsWith(`${DEEP_LINK_SCHEME}://`)) return arg;
  }
  return null;
}

/** 창을 앞으로 가져오고 SPA에 목적지를 보낸다. `S10`(show_answer)도 같은 함수를 쓴다 */
export function dispatchDeepLink(window: BrowserWindow, link: KnotDeepLink): void {
  focusWindow(window);
  window.webContents.send(IPC_CHANNELS.deepLink, link);
}

export function focusWindow(window: BrowserWindow): void {
  if (window.isMinimized()) window.restore();
  if (!window.isVisible()) window.show();
  window.focus();
}

export type AuthCallbackHandler = (callback: AuthCallbackDeepLink) => void;

export interface DeepLinkRouter {
  /** 문법에 맞으면 처리하고 true. 아니면 로그만 남기고 false */
  handleUrl(rawUrl: string): boolean;
  /** SPA 부팅 시 한 번 가져간다(`getPendingDeepLink`). 가져가면 비운다 */
  takePending(): KnotDeepLink | null;
  /** `knot://auth/callback` 수신자(A7). 해제 함수를 돌려준다 */
  onAuthCallback(handler: AuthCallbackHandler): () => void;
}

export interface DeepLinkRouterOptions {
  getWindow: () => BrowserWindow | null;
  /** 창이 없을 때(macOS에서 모두 닫힌 뒤) 새 창을 만든다 */
  openWindow: () => BrowserWindow;
  now?: () => number;
  pendingTtlMs?: number;
}

export function createDeepLinkRouter(options: DeepLinkRouterOptions): DeepLinkRouter {
  const now = options.now ?? Date.now;
  const ttl = options.pendingTtlMs ?? PENDING_DEEP_LINK_TTL_MS;
  const authHandlers = new Set<AuthCallbackHandler>();
  let pending: { link: KnotDeepLink; at: number } | null = null;

  function deliver(link: KnotDeepLink): void {
    pending = { link, at: now() };
    const existing = options.getWindow();
    if (existing === null) {
      // 콜드 스타트 또는 창이 모두 닫힌 상태. 새 창의 SPA가 부팅하며 보류 링크를 가져간다
      const created = options.openWindow();
      focusWindow(created);
      return;
    }
    if (existing.webContents.isLoading()) {
      focusWindow(existing);
      return;
    }
    dispatchDeepLink(existing, link);
  }

  return {
    handleUrl(rawUrl) {
      const target = parseDeepLink(rawUrl);
      if (target === null) {
        logger.warn("[knot] 딥링크 거부", { length: rawUrl.length });
        return false;
      }
      if (target.type === "auth") {
        // 코드·state 값은 남기지 않는다
        logger.info("[knot] 로그인 콜백 딥링크", { hasCode: target.code !== null, hasError: target.error !== null });
        for (const handler of authHandlers) handler(target);
        return true;
      }
      logger.info("[knot] 딥링크 수신", { type: target.type });
      deliver(target);
      return true;
    },
    takePending() {
      if (pending === null) return null;
      const { link, at } = pending;
      pending = null;
      if (now() - at > ttl) {
        logger.info("[knot] 보류 딥링크 만료");
        return null;
      }
      return link;
    },
    onAuthCallback(handler) {
      authHandlers.add(handler);
      return () => {
        authHandlers.delete(handler);
      };
    },
  };
}

/**
 * OS가 딥링크를 앱에 넘기는 세 경로를 라우터에 잇는다.
 *
 * `open-url`(macOS)은 `ready` 전에도 발화하므로 이 함수는 `app.whenReady()`보다 먼저 불러야 한다.
 * `second-instance`(Windows·Linux)는 `index.ts`의 단일 인스턴스 핸들러가 이미 있어 여기서는 인자만 본다.
 */
export function attachDeepLinkHandling(router: DeepLinkRouter): void {
  app.on("open-url", (event, url) => {
    event.preventDefault();
    router.handleUrl(url);
  });

  app.on("second-instance", (_event, argv) => {
    const url = findDeepLinkArg(argv);
    if (url !== null) router.handleUrl(url);
  });
}

/** 콜드 스타트 인자(Windows·Linux). `ready` 뒤 창을 만들기 직전에 부른다 */
export function handleColdStartDeepLink(router: DeepLinkRouter, argv: readonly string[] = process.argv): void {
  const url = findDeepLinkArg(argv);
  if (url !== null) router.handleUrl(url);
}

/** `knot://` 스킴을 이 앱에 등록한다. 미패키징 macOS에서는 실패하며 로그만 남긴다 */
export function registerProtocolClient(argv: readonly string[] = process.argv): boolean {
  let registered: boolean;
  if (process.defaultApp && argv[1] !== undefined) {
    // `electron .` 실행에서는 실행 파일이 electron 바이너리라 앱 경로를 인자로 준다
    registered = app.setAsDefaultProtocolClient(DEEP_LINK_SCHEME, process.execPath, [argv[1]]);
  } else {
    registered = app.setAsDefaultProtocolClient(DEEP_LINK_SCHEME);
  }
  if (!registered) {
    logger.warn("[knot] 딥링크 스킴 등록 실패(미패키징 빌드에서는 정상)", { scheme: DEEP_LINK_SCHEME });
  }
  return registered;
}
