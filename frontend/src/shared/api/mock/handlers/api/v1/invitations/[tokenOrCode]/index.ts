import { http, HttpResponse } from "msw";

import { invitationPreviewResponse } from "@api/mock/responses/workspaceInvitation";

export const invitationPreviewHandlers = [
  http.get("*/api/v1/invitations/:tokenOrCode", () =>
    HttpResponse.json(invitationPreviewResponse),
  ),
];
