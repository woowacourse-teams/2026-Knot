import { http, HttpResponse } from "msw";

import { notionOAuthAuthorizationResponse } from "@api/mock/responses/notionConnection";

// 경로 파라미터가 있어 fetch 상수 대신 패턴을 직접 적어요. 401·403·404는 테스트에서 덮어요
export const workspaceNotionOAuthAuthorizationsHandlers = [
  http.post(
    "*/api/v1/workspaces/:workspaceId/notion-oauth-authorizations",
    () => HttpResponse.json(notionOAuthAuthorizationResponse, { status: 201 }),
  ),
];
