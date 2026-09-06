/**
 * 애플리케이션 메뉴.
 *
 * 편집 메뉴는 macOS에서 복사·붙여넣기 단축키를 살리기 위해 반드시 필요하다.
 * 로그아웃 메뉴는 SPA 쪽 로그아웃 액션(W1)이 생긴 뒤에 붙인다.
 */

import { Menu, app, shell } from "electron";
import type { BrowserWindow, MenuItemConstructorOptions } from "electron";
import type { KnotEnvironment } from "../shared/env";
import { logFilePath, logger } from "./logging";
import { openExternalUrl } from "./navigation";
import { reloadWebOrigin } from "./windows";

export function buildApplicationMenu(
  env: KnotEnvironment,
  getWindow: () => BrowserWindow | null,
): void {
  const isMac = process.platform === "darwin";

  const template: MenuItemConstructorOptions[] = [
    ...(isMac
      ? ([
          {
            label: app.name,
            submenu: [
              { role: "about" },
              { type: "separator" },
              { role: "services" },
              { type: "separator" },
              { role: "hide" },
              { role: "hideOthers" },
              { role: "unhide" },
              { type: "separator" },
              { role: "quit" },
            ],
          },
        ] satisfies MenuItemConstructorOptions[])
      : []),
    {
      label: "파일",
      submenu: [
        {
          label: "Knot 다시 열기",
          accelerator: "CmdOrCtrl+Shift+R",
          click: () => {
            const window = getWindow();
            if (window !== null) reloadWebOrigin(window, env);
          },
        },
        { type: "separator" },
        isMac ? { role: "close" } : { role: "quit" },
      ],
    },
    {
      label: "편집",
      submenu: [
        { role: "undo" },
        { role: "redo" },
        { type: "separator" },
        { role: "cut" },
        { role: "copy" },
        { role: "paste" },
        { role: "selectAll" },
      ],
    },
    {
      label: "보기",
      submenu: [
        { role: "reload" },
        { role: "forceReload" },
        { role: "toggleDevTools" },
        { type: "separator" },
        { role: "resetZoom" },
        { role: "zoomIn" },
        { role: "zoomOut" },
        { type: "separator" },
        { role: "togglefullscreen" },
      ],
    },
    { role: "windowMenu", label: "창" },
    {
      role: "help",
      label: "도움말",
      submenu: [
        {
          label: "브라우저에서 열기",
          click: () => {
            openExternalUrl(env.webOrigin).catch((error: unknown) => {
              logger.warn("[knot] 브라우저 열기 실패", { error: String(error) });
            });
          },
        },
        {
          label: "로그 파일 열기",
          click: () => {
            void shell.showItemInFolder(logFilePath());
          },
        },
      ],
    },
  ];

  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}
