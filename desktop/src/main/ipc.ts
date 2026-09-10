/**
 * main 쪽 IPC 핸들러.
 *
 * 모든 `ipcMain.handle`은 `event.senderFrame`의 오리진이 웹 오리진일 때만
 * 처리한다(기획서 4.4·8절 #17). `senderFrame`이 null이면 거부한다.
 * 인자·반환값 중 액세스 토큰·연결 토큰은 로그에 남기지 않는다.
 */

import { ipcMain } from "electron";
import type { IpcMainInvokeEvent } from "electron";
import { IPC_CHANNELS } from "../shared/api";
import type { AgentBridgeStatus, KnotDeepLink, LlmSettingsView, LlmSubscriptionStatus } from "../shared/api";
import type { KnotEnvironment } from "../shared/env";
import { toOrigin } from "../shared/env";
import type { AgentBridge } from "./agent/bridge";
import { isValidAgentPort } from "./agent/bridgeConfig";
import { isAgentRegistrationTarget } from "./agent/registration";
import type { AuthController } from "./auth/loginFlow";
import type { DeepLinkRouter } from "./deepLink";
import { parseAnswerStreamInput } from "./llm/answerFlow";
import type { DesktopLlm } from "./llm/desktopLlm";
import { logger } from "./logging";
import { openExternalUrl } from "./navigation";
import { isNotificationInput } from "./notifications";
import type { DesktopNotificationInput } from "./notifications";
import { clearToken, readToken, writeToken } from "./tokenStore";

export interface IpcDependencies {
  bridge: AgentBridge;
  /** A8: 콜드 스타트 보류 링크를 SPA가 가져간다 */
  deepLinks: DeepLinkRouter;
  /** A7: 메인 창 안 로그인 뷰·로그아웃 */
  auth: AuthController;
  /** A10: renderer가 요청한 OS 알림. 표시 실패는 조용히 무시한다 */
  showNotification: (input: DesktopNotificationInput) => void;
  /** L1·L2: 사용자 Claude 구독 로그인·로그아웃·상태·답변 스트림. 토큰 값은 어떤 응답에도 싣지 않는다 */
  llm: DesktopLlm;
}

function assertTrustedSender(event: IpcMainInvokeEvent, env: KnotEnvironment, channel: string): void {
  const frame = event.senderFrame;
  if (frame === null) {
    logger.warn("[knot] IPC 거부: senderFrame 없음", { channel });
    throw new Error("허용되지 않은 호출자다.");
  }
  if (toOrigin(frame.url) !== env.webOrigin) {
    logger.warn("[knot] IPC 거부: 오리진 불일치", { channel, url: frame.url });
    throw new Error("허용되지 않은 호출자다.");
  }
}

export function registerIpcHandlers(env: KnotEnvironment, deps: IpcDependencies): void {
  const { bridge, deepLinks, auth, showNotification, llm } = deps;

  ipcMain.handle(IPC_CHANNELS.openExternal, async (event, rawUrl: unknown) => {
    assertTrustedSender(event, env, IPC_CHANNELS.openExternal);
    if (typeof rawUrl !== "string") {
      throw new Error("URL은 문자열이어야 한다.");
    }
    await openExternalUrl(rawUrl);
  });

  // A8: 앱이 꺼져 있을 때(또는 창 로드 중에) 들어온 딥링크를 SPA 부팅 시 한 번 넘긴다
  ipcMain.handle(IPC_CHANNELS.getPendingDeepLink, (event): KnotDeepLink | null => {
    assertTrustedSender(event, env, IPC_CHANNELS.getPendingDeepLink);
    return deepLinks.takePending();
  });

  // 토큰 세 채널은 저장소만 연다. 인자·반환값을 로그에 남기지 않는다(기획서 4.4).
  ipcMain.handle(IPC_CHANNELS.authGetToken, (event): string | null => {
    assertTrustedSender(event, env, IPC_CHANNELS.authGetToken);
    return readToken();
  });

  ipcMain.handle(IPC_CHANNELS.authSetToken, (event, rawToken: unknown) => {
    assertTrustedSender(event, env, IPC_CHANNELS.authSetToken);
    if (typeof rawToken !== "string" || rawToken.length === 0) {
      throw new Error("토큰은 비어 있지 않은 문자열이어야 한다.");
    }
    writeToken(rawToken);
  });

  ipcMain.handle(IPC_CHANNELS.authClearToken, (event) => {
    assertTrustedSender(event, env, IPC_CHANNELS.authClearToken);
    clearToken();
  });

  // A7: 메인 창 안 로그인 뷰(Q68). 실패·취소·타임아웃은 reject로 renderer에 전달된다(코드·state 값은 넘기지 않는다)
  ipcMain.handle(IPC_CHANNELS.authStartLogin, async (event) => {
    assertTrustedSender(event, env, IPC_CHANNELS.authStartLogin);
    await auth.startLogin();
  });

  // 로그인 뷰 헤더의 "취소"(2026-09-10, 기획서 5.2). 대기 중인 로그인이 없으면 아무 일도 하지 않는다
  ipcMain.handle(IPC_CHANNELS.authCancelLogin, (event) => {
    assertTrustedSender(event, env, IPC_CHANNELS.authCancelLogin);
    auth.cancelLogin();
  });

  ipcMain.handle(IPC_CHANNELS.authLogout, async (event) => {
    assertTrustedSender(event, env, IPC_CHANNELS.authLogout);
    await auth.logout();
  });

  // A10: renderer가 준 값은 믿지 않는다. 문구 길이·링크 모양을 다시 검사한다
  ipcMain.handle(IPC_CHANNELS.notificationsShow, (event, rawInput: unknown) => {
    assertTrustedSender(event, env, IPC_CHANNELS.notificationsShow);
    if (!isNotificationInput(rawInput)) {
      throw new Error("알림은 title(1~200자)·body(≤200자)·link(딥링크)만 받는다.");
    }
    showNotification({ title: rawInput.title, body: rawInput.body, link: rawInput.link ?? null });
  });

  // CLI 에이전트 연결(기획서 4.4 `agent.*`). 연결 토큰 값은 어떤 응답에도 싣지 않는다.
  ipcMain.handle(IPC_CHANNELS.agentStatus, (event): AgentBridgeStatus => {
    assertTrustedSender(event, env, IPC_CHANNELS.agentStatus);
    return bridge.getStatus();
  });

  ipcMain.handle(IPC_CHANNELS.agentCopyRegistration, (event, rawTarget: unknown): { preview: string } => {
    assertTrustedSender(event, env, IPC_CHANNELS.agentCopyRegistration);
    if (!isAgentRegistrationTarget(rawTarget)) {
      throw new Error("등록 대상은 claude-code·codex·gemini·skill 중 하나여야 한다.");
    }
    return bridge.copyRegistration(rawTarget);
  });

  ipcMain.handle(IPC_CHANNELS.agentRotateToken, (event) => {
    assertTrustedSender(event, env, IPC_CHANNELS.agentRotateToken);
    bridge.rotateToken();
  });

  ipcMain.handle(IPC_CHANNELS.agentSetPort, (event, rawPort: unknown) => {
    assertTrustedSender(event, env, IPC_CHANNELS.agentSetPort);
    if (!isValidAgentPort(rawPort)) {
      throw new Error("포트는 1024~65535 사이의 정수여야 한다.");
    }
    bridge.setPort(rawPort);
  });

  // L1: 사용자 Claude 구독(기획서 4.4 `llm.*`). 상태 객체에 토큰이 없고, 로그인 실패는 reject 메시지로만 전달된다
  ipcMain.handle(IPC_CHANNELS.llmStatus, (event): LlmSubscriptionStatus => {
    assertTrustedSender(event, env, IPC_CHANNELS.llmStatus);
    return llm.getStatus();
  });

  ipcMain.handle(IPC_CHANNELS.llmSignIn, async (event) => {
    assertTrustedSender(event, env, IPC_CHANNELS.llmSignIn);
    await llm.signIn();
  });

  ipcMain.handle(IPC_CHANNELS.llmSignOut, (event) => {
    assertTrustedSender(event, env, IPC_CHANNELS.llmSignOut);
    llm.signOut();
  });

  // L2: 앱 안 채팅 스트림. 입력은 main이 다시 검사하고, 이벤트는 요청한 renderer(sender)에만 보낸다
  ipcMain.handle(IPC_CHANNELS.llmStream, (event, rawInput: unknown) => {
    assertTrustedSender(event, env, IPC_CHANNELS.llmStream);
    const input = parseAnswerStreamInput(rawInput);
    const { sender } = event;
    llm.startStream(input, (payload) => {
      if (sender.isDestroyed()) return;
      sender.send(IPC_CHANNELS.llmStreamEvent, payload);
    });
  });

  ipcMain.handle(IPC_CHANNELS.llmStreamCancel, (event, rawInput: unknown) => {
    assertTrustedSender(event, env, IPC_CHANNELS.llmStreamCancel);
    const requestId = typeof rawInput === "object" && rawInput !== null ? (rawInput as { requestId?: unknown }).requestId : undefined;
    if (typeof requestId !== "string") {
      throw new Error("취소 요청에는 requestId 문자열이 있어야 한다.");
    }
    llm.cancelStream(requestId);
  });

  // L3: 설정. 값은 main이 허용 목록으로 다시 검사한다(목록 밖이면 reject)
  ipcMain.handle(IPC_CHANNELS.llmSettings, (event): LlmSettingsView => {
    assertTrustedSender(event, env, IPC_CHANNELS.llmSettings);
    return llm.getSettings();
  });

  ipcMain.handle(IPC_CHANNELS.llmUpdateSettings, (event, rawInput: unknown): LlmSettingsView => {
    assertTrustedSender(event, env, IPC_CHANNELS.llmUpdateSettings);
    return llm.updateSettings(rawInput);
  });
}
