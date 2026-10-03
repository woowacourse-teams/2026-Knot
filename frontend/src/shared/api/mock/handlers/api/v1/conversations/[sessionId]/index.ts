import { http, HttpResponse } from "msw";

import { chatMessagesResponse } from "@api/mock/responses/chatMessage";

export const chatMessagesHandlers = [
  http.get("*/api/v1/conversations/:sessionId", () =>
    HttpResponse.json(chatMessagesResponse),
  ),
];
