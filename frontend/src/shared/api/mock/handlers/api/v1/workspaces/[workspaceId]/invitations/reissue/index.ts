import { http, HttpResponse } from "msw";

import { workspaceInvitationResponse } from "@api/mock/responses/workspaceInvitation";

export const workspaceInvitationReissueHandlers = [
  http.post("*/api/v1/workspaces/:workspaceId/invitations/reissue", () =>
    HttpResponse.json(workspaceInvitationResponse, { status: 201 }),
  ),
];
