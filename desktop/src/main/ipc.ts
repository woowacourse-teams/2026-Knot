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
import type { AgentBridgeStatus, KnotDeepLink } from "../shared/api";
import type { KnotEnvironment } from "../shared/env";
import { toOrigin } from "../shared/env";
import type { AgentBridge } from "./agent/bridge";
import { isValidAgentPort } from "./agent/bridgeConfig";
import { isAgentRegistrationTarget } from "./agent/registration";
import { logger } from "./logging";
import { openExternalUrl } from "./navigation";
import { clearToken, readToken, writeToken } from "./tokenStore";

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

export function registerIpcHandlers(env: KnotEnvironment, bridge: AgentBridge): void {
  ipcMain.handle(IPC_CHANNELS.openExternal, async (event, rawUrl: unknown) => {
    assertTrustedSender(event, env, IPC_CHANNELS.openExternal);
    if (typeof rawUrl !== "string") {
      throw new Error("URL은 문자열이어야 한다.");
    }
    await openExternalUrl(rawUrl);
  });

  // 딥링크 수신은 A8에서 붙인다. 계약(기획서 4.4)을 먼저 고정해 두고
  // 지금은 항상 null을 돌려준다.
  ipcMain.handle(IPC_CHANNELS.getPendingDeepLink, (event): KnotDeepLink | null => {
    assertTrustedSender(event, env, IPC_CHANNELS.getPendingDeepLink);
    return null;
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
}
