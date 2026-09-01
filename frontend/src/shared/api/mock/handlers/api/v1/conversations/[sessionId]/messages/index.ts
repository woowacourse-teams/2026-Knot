import { delay, http, HttpResponse } from "msw";

import { chatMessageStreamResponse } from "@api/mock/responses/chatMessage";

/** 조각 사이의 간격. 한 번에 몰아 보내면 스트리밍이 아니라 한 덩어리 응답이 돼요 */
const CHUNK_INTERVAL = 10;

/** SSE 프레임 한 개. 빈 줄이 이벤트의 끝을 알립니다 */
const toFrame = (event: string, data: object) =>
  `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;

export const sendChatMessageHandlers = [
  http.post("*/api/v1/conversations/:sessionId/messages", () => {
    const encoder = new TextEncoder();
    const { deltas, messageId } = chatMessageStreamResponse;

    const stream = new ReadableStream({
      async start(controller) {
        for (const delta of deltas) {
          await delay(CHUNK_INTERVAL);
          controller.enqueue(encoder.encode(toFrame("chunk", { delta })));
        }

        await delay(CHUNK_INTERVAL);
        controller.enqueue(encoder.encode(toFrame("complete", { messageId })));
        controller.close();
      },
    });

    return new HttpResponse(stream, {
      headers: { "Content-Type": "text/event-stream" },
    });
  }),
];
