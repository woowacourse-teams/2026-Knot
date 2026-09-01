import { http, HttpResponse } from "msw";

import { workspaceDetailResponse } from "@api/mock/responses/workspace";

// 경로 파라미터가 있어 fetch의 상수를 그대로 못 쓰므로 패턴을 직접 적고, 폴더 위치로 대응 관계를 드러내요
export const workspaceHandlers = [
  http.get("*/api/v1/workspaces/:workspaceId", () =>
    HttpResponse.json(workspaceDetailResponse),
  ),
];
