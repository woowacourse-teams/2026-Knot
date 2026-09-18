import { http, HttpResponse } from "msw";

import { workspaceDetailResponse } from "@api/mock/responses/workspace";

// 경로 파라미터가 있어 fetch 상수 대신 패턴을 직접 적어요
export const workspaceHandlers = [
  http.get("*/api/v1/workspaces/:workspaceId", () =>
    HttpResponse.json(workspaceDetailResponse),
  ),
];
