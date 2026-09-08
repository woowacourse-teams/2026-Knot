/**
 * 네비게이션 허용 목록·새 창·권한 정책.
 *
 * 근거: 기획서 4.5(오리진 정책), 8절 체크리스트 #5·#11~#15.
 * 차단은 `will-navigate`와 `will-redirect` 두 곳 모두에 건다 — `will-navigate`는
 * 네비게이션 *시작*에서만 발화하고, 그 도중의 서버 302는 `will-redirect`로
 * 발화한다. OAuth는 302 체인이라 한쪽만 막으면 목록 밖으로 새어 나간다
 * (기획서 4.5 정정 2026-09-06).
 */

import { shell } from "electron";
import type {
  Event,
  Session,
  WebContents,
  WebContentsWillNavigateEventParams,
  WebContentsWillRedirectEventParams,
  WindowOpenHandlerResponse,
} from "electron";
import type { KnotEnvironment } from "../shared/env";
import { toOrigin } from "../shared/env";
import { logger } from "./logging";

/** 웹 오리진에서 온 요청에만 허용하는 권한(기획서 8절 #5) */
const ALLOWED_PERMISSIONS = new Set([
  "notifications",
  "clipboard-read",
  "clipboard-sanitized-write",
]);

/** 앱 창 안에서 이동해도 되는 URL인가 */
export function isAllowedNavigation(rawUrl: string, allowlist: readonly string[]): boolean {
  const origin = toOrigin(rawUrl);
  return origin !== null && allowlist.includes(origin);
}

/**
 * 자식 창에 다시 명시하는 보안 webPreferences(기획서 8절 #2~#4·#11, 불변 계약 4).
 *
 * Electron은 보안 관련 webPreferences를 부모에서 상속하지만, 창을 추가할 때도 같은
 * 값을 쓴다는 규칙(`desktop/CLAUDE.md`)을 코드에서 보이게 둔다. preload는 상속되지
 * 않으므로 자식 창에는 `window.knotDesktop`이 없다.
 */
const CHILD_WINDOW_WEB_PREFERENCES = {
  sandbox: true,
  contextIsolation: true,
  nodeIntegration: false,
  webviewTag: false,
} as const;

/**
 * 새 창 요청(`window.open`·`target=_blank`)을 어떻게 처리할지 정한다(기획서 4.5, 로드맵 Q46).
 *
 * 허용 목록 안 오리진은 자식 창으로 연다. Notion 로그인 화면의 IdP 팝업
 * (`app.notion.com/verifyNoPopupBlockerHtmlAndRedirect`)은 `window.opener`가 없으면
 * 스스로 닫히므로 외부 브라우저로는 통과할 수 없다(2026-09-08 실측, U27). 자식 창은
 * opener의 세션을 쓰고 `web-contents-created`로 같은 네비게이션 정책을 받는다.
 * 목록 밖은 거부하고 호출자가 외부 브라우저로 넘긴다.
 */
export function resolveWindowOpen(
  rawUrl: string,
  allowlist: readonly string[],
): WindowOpenHandlerResponse {
  if (!isAllowedNavigation(rawUrl, allowlist)) {
    return { action: "deny" };
  }
  return {
    action: "allow",
    overrideBrowserWindowOptions: {
      webPreferences: { ...CHILD_WINDOW_WEB_PREFERENCES },
    },
  };
}

/** 외부 브라우저로 넘겨도 되는 URL인가(기획서 8절 #15) */
export function isSafeExternalUrl(rawUrl: string): boolean {
  let url: URL;
  try {
    url = new URL(rawUrl);
  } catch {
    return false;
  }
  return url.protocol === "https:" || url.protocol === "mailto:";
}

/** 검증에 실패하면 열지 않고 던진다. renderer에는 reject로 전달된다(기획서 4.4) */
export async function openExternalUrl(rawUrl: string): Promise<void> {
  if (!isSafeExternalUrl(rawUrl)) {
    logger.warn("[knot] 외부 열기 거부", { url: rawUrl });
    throw new Error("https: 또는 mailto: URL만 외부에서 열 수 있다.");
  }
  await shell.openExternal(rawUrl);
}

/** 열 수 없는 URL이면 조용히 버린다. 이벤트 핸들러에서 쓰는 fire-and-forget 경로 */
function openExternalBestEffort(rawUrl: string): void {
  openExternalUrl(rawUrl).catch((error: unknown) => {
    logger.warn("[knot] 외부 열기 실패", { url: rawUrl, error: String(error) });
  });
}

/** `will-navigate`·`will-redirect`가 넘기는 이벤트. 둘 다 preventDefault로 취소된다 */
type BlockableNavigation =
  | Event<WebContentsWillNavigateEventParams>
  | Event<WebContentsWillRedirectEventParams>;

/**
 * 창 하나에 네비게이션 정책을 건다.
 *
 * `app.on("web-contents-created")`에서 호출해 새로 만들어지는 창까지 덮는다.
 */
export function applyNavigationPolicy(contents: WebContents, env: KnotEnvironment): void {
  const guard = (details: BlockableNavigation, event: "will-navigate" | "will-redirect"): void => {
    if (isAllowedNavigation(details.url, env.navigationAllowlist)) {
      logger.info("[knot] 네비게이션 허용", { event, url: details.url });
      return;
    }
    details.preventDefault();
    logger.warn("[knot] 네비게이션 차단 → 외부 브라우저", { event, url: details.url });
    openExternalBestEffort(details.url);
  };

  contents.on("will-navigate", (details) => {
    guard(details, "will-navigate");
  });
  contents.on("will-redirect", (details) => {
    guard(details, "will-redirect");
  });

  // 허용 목록 안은 자식 창(OAuth 로그인 팝업), 밖은 거부 + 검증된 URL만 외부 브라우저(기획서 8절 #14)
  contents.setWindowOpenHandler(({ url }) => {
    const response = resolveWindowOpen(url, env.navigationAllowlist);
    if (response.action === "allow") {
      logger.info("[knot] 새 창 허용 → 자식 창", { url });
      return response;
    }
    logger.info("[knot] 새 창 요청 거부 → 외부 브라우저", { url });
    openExternalBestEffort(url);
    return response;
  });

  // `webviewTag`는 기본 false지만 attach 시도 자체를 막는다(기획서 8절 #11~12)
  contents.on("will-attach-webview", (event) => {
    event.preventDefault();
    logger.warn("[knot] webview attach 거부");
  });

  // 아래 둘은 취소할 수 없다. OAuth 302 체인 실측 기록 전용이다(로드맵 U1·U2).
  contents.on("did-start-navigation", (details) => {
    if (!details.isMainFrame || details.isSameDocument) return;
    logger.info("[knot] 네비게이션 시작", { url: details.url });
  });
  contents.on("did-redirect-navigation", (details) => {
    if (!details.isMainFrame) return;
    logger.info("[knot] 리다이렉트", { url: details.url });
  });
}

/**
 * 세션 권한 정책(기획서 8절 #5).
 *
 * 웹 오리진에서 온 요청의 알림·클립보드만 허용하고 나머지는 전부 거부한다.
 */
export function applySessionPolicy(session: Session, env: KnotEnvironment): void {
  session.setPermissionRequestHandler((_contents, permission, callback, details) => {
    const origin = toOrigin(details.requestingUrl ?? "");
    const allowed = origin === env.webOrigin && ALLOWED_PERMISSIONS.has(permission);
    if (!allowed) {
      logger.warn("[knot] 권한 요청 거부", { permission, origin });
    }
    callback(allowed);
  });

  session.setPermissionCheckHandler((_contents, permission, requestingOrigin) => {
    return requestingOrigin === env.webOrigin && ALLOWED_PERMISSIONS.has(permission);
  });

  // HID·시리얼·USB 기기 접근은 쓰지 않는다
  session.setDevicePermissionHandler(() => false);
}
