import { http, HttpResponse } from "msw";

import { LAST_VIEWED_WORKSPACE_API_PATH } from "@api/fetch/api/v1/members/me/lastViewedWorkspace";

// 성공 시 204로 응답 본문이 없는 엔드포인트예요
export const lastViewedWorkspaceHandlers = [
  http.put(
    `*${LAST_VIEWED_WORKSPACE_API_PATH}`,
    () => new HttpResponse(null, { status: 204 }),
  ),
];
