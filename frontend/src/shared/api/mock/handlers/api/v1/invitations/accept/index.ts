import { http, HttpResponse } from "msw";

import { INVITATIONS_ACCEPT_API_PATH } from "@api/fetch/api/v1/invitations/accept";
import { invitationAcceptanceResponse } from "@api/mock/responses/workspaceInvitation";

// 기본값은 새 멤버십을 만든 201이에요. 기존 멤버십(200)은 테스트에서 덮어요
export const invitationAcceptHandlers = [
  http.post(`*${INVITATIONS_ACCEPT_API_PATH}`, () =>
    HttpResponse.json(invitationAcceptanceResponse, { status: 201 }),
  ),
];
