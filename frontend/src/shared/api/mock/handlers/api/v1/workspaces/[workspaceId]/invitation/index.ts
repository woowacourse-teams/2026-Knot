import { http, HttpResponse } from "msw";

import { workspaceInvitationResponse } from "@api/mock/responses/workspaceInvitation";

export const workspaceInvitationHandlers = [
  http.get("*/api/v1/workspaces/:workspaceId/invitation", () =>
    HttpResponse.json(workspaceInvitationResponse),
  ),
];
