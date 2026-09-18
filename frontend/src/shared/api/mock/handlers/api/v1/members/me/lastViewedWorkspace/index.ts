import { http, HttpResponse } from "msw";

import { LAST_VIEWED_WORKSPACE_API_PATH } from "@api/fetch/api/v1/members/me/lastViewedWorkspace";

export const lastViewedWorkspaceHandlers = [
  http.put(
    `*${LAST_VIEWED_WORKSPACE_API_PATH}`,
    () => new HttpResponse(null, { status: 204 }),
  ),
];
