import { http, HttpResponse } from "msw";

import { notionConnectionResponse } from "@api/mock/responses/notionConnection";

// 경로 파라미터가 있어 fetch 상수 대신 패턴을 직접 적어요. NOT_CONNECTED·REAUTH_REQUIRED·에러는 테스트에서 덮어요
export const workspaceNotionConnectionHandlers = [
  http.get("*/api/v1/workspaces/:workspaceId/notion-connection", () =>
    HttpResponse.json(notionConnectionResponse),
  ),
];
