import { http, HttpResponse } from "msw";

import {
  chatSessionResponse,
  chatSessionsResponse,
} from "@api/mock/responses/chatSession";

export const workspaceConversationsHandlers = [
  http.get("*/api/v1/workspaces/:workspaceId/conversations", () =>
    HttpResponse.json(chatSessionsResponse),
  ),
  http.post("*/api/v1/workspaces/:workspaceId/conversations", () =>
    HttpResponse.json(chatSessionResponse),
  ),
];
