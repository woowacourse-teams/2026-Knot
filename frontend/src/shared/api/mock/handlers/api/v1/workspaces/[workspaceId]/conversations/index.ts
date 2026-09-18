import { http, HttpResponse } from "msw";

import {
  chatSessionResponse,
  chatSessionsResponse,
} from "@api/mock/responses/chatSession";

export const workspaceConversationsHandlers = [
  http.get("*/api/v1/workspaces/:workspaceId/conversations", () =>
    HttpResponse.json(chatSessionsResponse),
  ),
  // 생성은 201로 응답해요. 실제 서버도 Location 헤더와 함께 201을 줍니다
  http.post("*/api/v1/workspaces/:workspaceId/conversations", () =>
    HttpResponse.json(chatSessionResponse, { status: 201 }),
  ),
];
