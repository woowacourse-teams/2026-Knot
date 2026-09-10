/**
 * 애플리케이션 메뉴.
 *
 * 편집 메뉴는 macOS에서 복사·붙여넣기 단축키를 살리기 위해 반드시 필요하다.
 * `파일` 메뉴의 로그인·로그아웃은 `A7` 메인 창 안 로그인 뷰 경로다(로드맵 Q60·Q68) — SPA의 로그아웃
 * 액션(GNB)과 같은 결과를 내며 진입점만 하나 더 있는 것이다(기획서 5.1).
 *
 * `CLI 에이전트 연결` 메뉴는 `S9` 연결 안내 화면(웹 SPA)이 생기기 전까지의 임시 진입점이다
 * (로드맵 4.5절 `S8` 착수 가정 1). 등록 스니펫은 main이 클립보드에 쓰고, 대화상자에는
 * 토큰을 가린 미리보기만 보인다.
 *
 * `Claude 구독` 메뉴는 `L3` 설정 화면(웹 SPA `/claude-subscription`)으로 가는 진입점 하나다(로드맵 Q64·Q67).
 * 로그인·로그아웃·모델 설정은 그 화면이 preload `llm` API로 한다.
 */

import { Menu, app, dialog, shell } from "electron";
import type { BrowserWindow, MenuItemConstructorOptions } from "electron";
import type { AgentRegistrationTarget } from "../shared/api";
import type { KnotEnvironment } from "../shared/env";
import type { AgentBridge } from "./agent/bridge";
import type { AuthController } from "./auth/loginFlow";
import type { SubscriptionController } from "./llm/subscriptionFlow";
import { logFilePath, logger } from "./logging";
import { openExternalUrl } from "./navigation";
import { reloadWebOrigin } from "./windows";

export interface ApplicationMenuOptions {
  env: KnotEnvironment;
  getWindow: () => BrowserWindow | null;
  bridge: AgentBridge;
  /** A7 메인 창 안 로그인 뷰·로그아웃 */
  auth: AuthController;
  /** L1 사용자 Claude 구독 로그인·로그아웃·상태 */
  llm: SubscriptionController;
  /** A4 "업데이트 확인". 켜져 있지 않은 빌드는 호출자가 안내한다 */
  checkForUpdates: () => void;
}

export function buildApplicationMenu(options: ApplicationMenuOptions): void {
  const { env, getWindow, bridge, auth, llm, checkForUpdates } = options;
  const isMac = process.platform === "darwin";

  const copyItem = (label: string, target: AgentRegistrationTarget): MenuItemConstructorOptions => ({
    label,
    click: () => {
      try {
        const { preview } = bridge.copyRegistration(target);
        void dialog.showMessageBox({
          type: "info",
          title: "Knot",
          message: "클립보드에 복사했어요. 터미널에 붙여 넣어 실행하세요.",
          detail: preview,
        });
      } catch (error) {
        logger.warn("[knot] 등록 스니펫 복사 실패", { target, error: String(error) });
      }
    },
  });

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
        {
          // A7: 앱 안 자식 창에서 로그인한다(기획서 5.2, 로드맵 Q68 — 이전에는 시스템 브라우저)
          label: "로그인",
          click: () => {
            auth.startLogin().catch((error: unknown) => {
              const message = error instanceof Error ? error.message : "로그인하지 못했어요.";
              logger.warn("[knot] 로그인 실패", { reason: error instanceof Error ? error.name : "Unknown" });
              void dialog.showMessageBox({ type: "warning", title: "Knot", message });
            });
          },
        },
        {
          label: "로그아웃",
          click: () => {
            auth.logout().catch((error: unknown) => {
              logger.warn("[knot] 로그아웃 실패", { reason: error instanceof Error ? error.name : "Unknown" });
            });
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
    {
      label: "CLI 에이전트 연결",
      submenu: [
        {
          // S9 연결 안내 화면(웹 SPA `/agent-connection`, 데스크톱 전용 라우트)
          label: "연결 안내 화면 열기",
          click: () => {
            const window = getWindow();
            if (window === null) return;
            logger.info("[knot] 연결 안내 화면 이동");
            void window.loadURL(`${env.webOrigin}/agent-connection`);
          },
        },
        { type: "separator" },
        copyItem("Claude Code 등록 명령 복사", "claude-code"),
        copyItem("Codex CLI 설정 복사", "codex"),
        copyItem("Gemini CLI 등록 명령 복사", "gemini"),
        copyItem("Knot 스킬 설치 명령 복사", "skill"),
        { type: "separator" },
        {
          label: "연결 상태 보기",
          click: () => {
            const status = bridge.getStatus();
            void dialog.showMessageBox({
              type: status.error === null ? "info" : "warning",
              title: "Knot",
              message: status.running ? "로컬 MCP 서버가 실행 중이에요." : "로컬 MCP 서버가 실행 중이 아니에요.",
              detail: [
                `URL: ${status.url}`,
                `토큰 발급: ${status.tokenIssuedAt}`,
                `마지막 도구 호출: ${status.lastToolCallAt ?? "없음"}`,
                `스킬 파일: ${status.skillPath}`,
                ...(status.error === null ? [] : [`오류: ${status.error}`]),
              ].join("\n"),
            });
          },
        },
        {
          label: "연결 토큰 재발급",
          click: () => {
            void dialog
              .showMessageBox({
                type: "question",
                title: "Knot",
                message: "연결 토큰을 재발급할까요?",
                detail: "기존에 등록한 CLI 설정은 무효가 되어 등록 명령을 다시 실행해야 해요.",
                buttons: ["재발급", "취소"],
                defaultId: 1,
                cancelId: 1,
              })
              .then(({ response }) => {
                if (response === 0) bridge.rotateToken();
              });
          },
        },
      ],
    },
    {
      // L1~L3: 사용자 본인의 Claude 구독으로 앱 안 탐색에 답한다(기획서 6.5). 설정 화면은 웹 SPA(데스크톱 전용 라우트)
      label: "Claude 구독",
      submenu: [
        {
          label: "구독 설정 열기",
          click: () => {
            const window = getWindow();
            if (window === null) return;
            logger.info("[knot] 구독 설정 화면 이동", { signedIn: llm.getStatus().signedIn });
            void window.loadURL(`${env.webOrigin}/claude-subscription`);
          },
        },
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
        { type: "separator" },
        {
          // A4: 정책(Q59)상 꺼진 빌드에서는 호출자가 이유를 안내한다
          label: "업데이트 확인",
          click: checkForUpdates,
        },
      ],
    },
  ];

  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}
