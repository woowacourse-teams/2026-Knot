import { http, HttpResponse } from "msw";

import { notionPageTreeResponse } from "@api/mock/responses/notionPage";

// 경로 파라미터가 있어 fetch 상수 대신 패턴을 직접 적어요
export const workspaceNotionPageTreeHandlers = [
  http.get("*/api/v1/workspaces/:workspaceId/notion-pages/tree", () =>
    HttpResponse.json(notionPageTreeResponse),
  ),
];
