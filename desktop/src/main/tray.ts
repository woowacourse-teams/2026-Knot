/**
 * 트레이 아이콘·글로벌 단축키 (기획서 7절 P2, 로드맵 A9).
 *
 * 트레이는 "앱 열기 · 퀵 질문 · 종료"만 둔다. 상시 실행이 목적이라 macOS에서는 창을 모두
 * 닫아도 앱이 남고(`index.ts`의 `window-all-closed`), 트레이가 다시 여는 진입점이다.
 * 아이콘은 `resources/tray/knotTemplate.png`(검정 + 알파 템플릿 이미지, `@2x` 포함)이며
 * macOS 메뉴 막대의 밝기·다크 모드를 OS가 맞춘다.
 */

import { Menu, Tray, globalShortcut, nativeImage } from "electron";
import type { MenuItemConstructorOptions } from "electron";
import { logger } from "./logging";

export interface TrayActions {
  onOpen: () => void;
  onQuickAsk: () => void;
  onQuit: () => void;
  quickAskAccelerator: string;
}

/** 트레이 메뉴 템플릿. Electron 객체를 만들지 않아 vitest로 검증한다 */
export function buildTrayMenuTemplate(actions: TrayActions): MenuItemConstructorOptions[] {
  return [
    { label: "Knot 열기", click: actions.onOpen },
    { label: "퀵 질문", accelerator: actions.quickAskAccelerator, click: actions.onQuickAsk },
    { type: "separator" },
    { label: "종료", click: actions.onQuit },
  ];
}

export function createTray(iconPath: string, actions: TrayActions): Tray {
  const image = nativeImage.createFromPath(iconPath);
  if (image.isEmpty()) {
    logger.warn("[knot] 트레이 아이콘을 읽지 못했다", { iconPath });
  }
  image.setTemplateImage(true);

  const tray = new Tray(image);
  tray.setToolTip("Knot");
  tray.setContextMenu(Menu.buildFromTemplate(buildTrayMenuTemplate(actions)));
  // Windows·Linux는 왼쪽 클릭이 메뉴를 열지 않는다. macOS는 컨텍스트 메뉴가 있으면 클릭이 메뉴를 연다
  if (process.platform !== "darwin") {
    tray.on("click", actions.onOpen);
  }
  return tray;
}

/** 글로벌 단축키 등록. 다른 앱이 이미 잡고 있으면 false이며 메뉴·트레이로만 연다 */
export function registerQuickAskShortcut(accelerator: string, handler: () => void): boolean {
  let registered = false;
  try {
    registered = globalShortcut.register(accelerator, handler);
  } catch (error) {
    logger.warn("[knot] 단축키 등록 오류", { accelerator, reason: String(error) });
    return false;
  }
  if (!registered) {
    logger.warn("[knot] 단축키 등록 실패(다른 앱이 사용 중)", { accelerator });
  }
  return registered;
}

export function unregisterAllShortcuts(): void {
  globalShortcut.unregisterAll();
}
