import { http, HttpResponse } from "msw";

import { chatMessagesResponse } from "@api/mock/responses/chatMessage";
import { getSentChatMessages } from "@api/mock/store/chatMessage";

export const chatMessagesHandlers = [
  // 고정 이력 뒤에, 이 화면에서 주고받아 저장된 대화를 이어 붙여요
  http.get("*/api/v1/conversations/:sessionId", ({ params }) =>
    HttpResponse.json([
      ...chatMessagesResponse,
      ...getSentChatMessages(String(params.sessionId)),
    ]),
  ),
];
