/**
 * 메인 창 안 로그인 뷰 — Electron 접착층 (기획서 5.2 "Electron 측", 로드맵 `A7`·Q68·Q69).
 *
 * 2단계 로그인의 인가 URL을 **메인 창 `contentView`에 붙이는 `WebContentsView`**에서 연다
 * (재개정 2026-09-10, 사용자 지시). 로그인 때문에 `BrowserWindow`를 새로 만들지 않는다 —
 * 자식 창·모달·시스템 브라우저 전부 금지다. preload를 주지 않고 보안 webPreferences 4종을
 * 다시 명시하며(불변 계약 4), 웹 세션(`persist:knot`)을 공유해 GitHub 세션 쿠키가 로그아웃
 * 전까지 남는다. 백엔드가 마지막에 302로 보내는 loopback 콜백 오리진은 **이 뷰에만** 한시로
 * 허용한다(기획서 4.5) — 뷰를 떼면 허용도 사라진다. URL·쿼리(코드·state)는 로그에 남기지 않는다.
 */

import { WebContentsView } from "electron";
import type { BrowserWindow, Rectangle, Session } from "electron";
import { logger } from "../logging";
import { allowExtraOrigins, applyLoginViewWindowPolicy, revokeExtraOrigins } from "../navigation";
import type { KnotEnvironment } from "../../shared/env";

/**
 * 뷰가 덮지 않고 남기는 상단 띠의 높이(px).
 *
 * 이 띠에는 메인 창의 SPA가 제목과 "취소"를 그린다(기획서 5.2). 웹이 그리지 않아도
 * 뷰 안 `Esc`로 빠져나올 수 있다(로드맵 R31).
 */
export const LOGIN_HEADER_HEIGHT = 44;

/** 메인 창 content 영역에서 로그인 뷰가 차지할 사각형. 헤더 띠만 남기고 덮는다 */
export function resolveLoginViewBounds(content: { width: number; height: number }): Rectangle {
  return {
    x: 0,
    y: LOGIN_HEADER_HEIGHT,
    width: Math.max(content.width, 0),
    height: Math.max(content.height - LOGIN_HEADER_HEIGHT, 0),
  };
}

export interface LoginViewOptions {
  /** 빌드 상수 API 오리진으로 조립한 인가 URL(호출자가 검증한다) */
  url: string;
  /** 이 뷰에만 한시로 허용할 loopback 콜백 오리진 */
  callbackOrigin: string;
  /** 뷰를 붙일 메인 창 */
  window: BrowserWindow;
  /** 웹 세션(`persist:knot`) */
  session: Session;
  /** 오리진 정책을 걸 때 쓰는 빌드 상수 환경 */
  env: KnotEnvironment;
  /** 사용자가 취소했다(뷰 안 `Esc`·메인 창 닫힘). 셸이 뗀 경우에는 부르지 않는다 */
  onCancelled(): void;
}

export interface LoginViewHandle {
  /** 이미 로그인 중일 때 뷰를 새로 만들지 않고 포커스만 준다 */
  focus(): void;
  /** 셸이 뗀다. `onCancelled`는 부르지 않는다 */
  close(): void;
}

export function openLoginView(options: LoginViewOptions): LoginViewHandle {
  const { window } = options;
  const view = new WebContentsView({
    webPreferences: {
      session: options.session,
      sandbox: true,
      contextIsolation: true,
      nodeIntegration: false,
      webviewTag: false,
    },
  });

  // 콜백(`http://127.0.0.1:{port}/callback`)은 빌드 상수 목록에 없다. 이 뷰에만 연다
  allowExtraOrigins(view.webContents, [options.callbackOrigin]);
  // 로그인 뷰에서는 창을 하나도 만들지 않는다(로드맵 Q69). `web-contents-created`가 건
  // 기본 정책(허용 목록 안이면 자식 창)을 여기서 덮어쓴다
  applyLoginViewWindowPolicy(view.webContents, options.env);

  const layout = (): void => {
    if (view.webContents.isDestroyed() || window.isDestroyed()) return;
    const { width, height } = window.getContentBounds();
    view.setBounds(resolveLoginViewBounds({ width, height }));
  };

  let closedByShell = false;

  function cancel(reason: string): void {
    if (closedByShell) return;
    closedByShell = true;
    logger.info("[knot] 로그인 뷰 취소", { reason });
    detach();
    options.onCancelled();
  }

  function detach(): void {
    window.off("resize", layout);
    window.off("closed", onWindowClosed);
    if (window.isDestroyed()) return;
    revokeExtraOrigins(view.webContents);
    window.contentView.removeChildView(view);
    if (!view.webContents.isDestroyed()) view.webContents.close();
  }

  function onWindowClosed(): void {
    cancel("메인 창 닫힘");
  }

  // 웹이 헤더 띠를 그리지 않는 셸·웹 조합에서도 빠져나올 수 있어야 한다(로드맵 R31)
  view.webContents.on("before-input-event", (event, input) => {
    if (input.type !== "keyDown" || input.key !== "Escape") return;
    event.preventDefault();
    cancel("Esc");
  });

  view.webContents.on("did-fail-load", (_event, errorCode, errorDescription, _url, isMainFrame) => {
    // ERR_ABORTED(-3)는 셸이 뷰를 뗄 때도 온다. 오류로 보지 않는다
    if (!isMainFrame || errorCode === -3) return;
    logger.error("[knot] 로그인 뷰 로드 실패", { errorCode, errorDescription });
  });

  window.contentView.addChildView(view);
  layout();
  window.on("resize", layout);
  window.once("closed", onWindowClosed);

  logger.info("[knot] 로그인 뷰 열기");
  void view.webContents.loadURL(options.url);
  view.webContents.focus();

  return {
    focus: () => {
      if (view.webContents.isDestroyed()) return;
      if (!window.isDestroyed() && window.isMinimized()) window.restore();
      view.webContents.focus();
    },
    close: () => {
      if (closedByShell) return;
      closedByShell = true;
      detach();
    },
  };
}
