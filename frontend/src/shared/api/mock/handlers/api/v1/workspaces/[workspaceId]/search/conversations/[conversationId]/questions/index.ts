import { http } from "msw";

import { toSearchAnswerStreamResponse } from "@api/mock/handlers/api/v1/workspaces/[workspaceId]/search/conversations";
import { searchQuestionAnswerStreamResponse } from "@api/mock/responses/search";

export const workspaceSearchConversationQuestionsHandlers = [
  http.post(
    "*/api/v1/workspaces/:workspaceId/search/conversations/:conversationId/questions",
    // 실제 서버처럼 질문을 보낸 대화의 ID로 accepted를 보내요
    ({ params }) =>
      toSearchAnswerStreamResponse({
        ...searchQuestionAnswerStreamResponse,
        conversationId: Number(params.conversationId),
      }),
  ),
];
