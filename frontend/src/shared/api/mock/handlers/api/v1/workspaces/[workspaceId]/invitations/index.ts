import { http, HttpResponse } from "msw";

import { workspaceInvitationResponse } from "@api/mock/responses/workspaceInvitation";

// 기본값은 기존 활성 초대를 돌려주는 200이에요. 새 초대(201)는 테스트에서 덮어요
export const workspaceInvitationsHandlers = [
  http.post("*/api/v1/workspaces/:workspaceId/invitations", () =>
    HttpResponse.json(workspaceInvitationResponse),
  ),
];
