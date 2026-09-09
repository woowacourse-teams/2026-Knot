import { chatMessageSourcesResponse } from "@api/mock/responses/chatMessage";
import { http, HttpResponse } from "msw";

export const chatMessageSourcesHandlers = [
  // 어느 답변이든 같은 출처 8건을 돌려줘요. 빈 목록·오류는 테스트에서 덮어요
  http.get("*/api/v1/messages/:messageId/sources", () =>
    HttpResponse.json(chatMessageSourcesResponse),
  ),
];
