import type { KnotDeepLink } from "@/shared/types/desktop";

import { getRouterPath } from "./PATH_ROUTE";

/**
 * 데스크톱 셸이 넘겨 준 딥링크(`knot://…`)를 이 앱의 경로로 바꿔요.
 *
 * - `invite` → 초대 링크 판정 화면(`/invite/:token`). 웹 초대 링크와 같은 화면이라 이후 흐름이 같아요.
 * - `chat` → 세션이 있으면 그 대화(`/workspace/:workspaceId/chat/:sessionId`), 없으면 새 대화 화면.
 *   CLI 에이전트가 `show_answer`로 저장한 답을 보여 줄 때도 이 모양으로 와요(기획서 6.4).
 *
 * 값의 문법(토큰 문자·양의 정수)은 셸이 이미 검사했고, 실제로 존재하는지는 각 화면이 서버에 물어 판단해요.
 * 모르는 모양이면 `null`을 돌려주고 이동하지 않습니다. 셸이 웹보다 늦게 업데이트되므로 여기서 던지지 않아요.
 *
 * @see docs/electron-desktop-app-tech-plan.md 4.3 딥링크 초대
 */
export const resolveDeepLinkPath = (link: KnotDeepLink): string | null => {
  switch (link.type) {
    case "invite":
      return getRouterPath({
        routeKey: "INVITE",
        params: { token: link.token },
      });
    case "chat":
      if (link.sessionId === undefined) {
        return getRouterPath({
          routeKey: "CHAT",
          params: { workspaceId: link.workspaceId },
        });
      }
      return getRouterPath({
        routeKey: "CHAT_SESSION",
        params: { workspaceId: link.workspaceId, sessionId: link.sessionId },
      });
    default:
      return null;
  }
};
