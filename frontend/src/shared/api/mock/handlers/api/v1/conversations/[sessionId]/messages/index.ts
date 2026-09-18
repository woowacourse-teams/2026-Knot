import { delay, http, HttpResponse } from "msw";

import { chatMessageStreamResponse } from "@api/mock/responses/chatMessage";
import { appendSentChatMessages } from "@api/mock/store/chatMessage";

/**
 * 조각 사이의 간격(ms).
 *
 * 한 번에 몰아 보내면 스트리밍이 아니라 한 덩어리 응답이 돼요. 사람이 글자가 늘어나는 걸
 * 알아볼 수 있는 속도로 띄웁니다. 실제 LLM의 토큰 속도와 비슷한 범위예요.
 */
const CHUNK_INTERVAL = 80;

/** SSE 프레임 한 개. 빈 줄이 이벤트의 끝을 알립니다 */
const toFrame = (event: string, data: object) =>
  `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;

interface SendChatMessageRequestBody {
  content: string;
}

export const sendChatMessageHandlers = [
  http.post("*/api/v1/conversations/:sessionId/messages", async ({ request, params }) => {
    const encoder = new TextEncoder();
    const { deltas } = chatMessageStreamResponse;
    const { content } = (await request.json()) as SendChatMessageRequestBody;

    const stream = new ReadableStream({
      async start(controller) {
        for (const delta of deltas) {
          await delay(CHUNK_INTERVAL);
          controller.enqueue(encoder.encode(toFrame("chunk", { delta })));
        }

        await delay(CHUNK_INTERVAL);

        // 실제 서버처럼 답변까지 저장한 뒤에 그 ID를 알려 줍니다
        const messageId = appendSentChatMessages({
          sessionId: String(params.sessionId),
          question: content,
          answer: deltas.join(""),
        });

        controller.enqueue(encoder.encode(toFrame("complete", { messageId })));
        controller.close();
      },
    });

    return new HttpResponse(stream, {
      headers: { "Content-Type": "text/event-stream" },
    });
  }),
];
