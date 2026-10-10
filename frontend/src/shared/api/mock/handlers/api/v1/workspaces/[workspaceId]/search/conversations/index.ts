import { delay, http, HttpResponse } from "msw";

import { searchAnswerStreamResponse } from "@api/mock/responses/search";

/**
 * 프레임 사이의 간격(ms).
 *
 * 한 번에 몰아 보내면 스트리밍이 아니라 한 덩어리 응답이 돼요. 사람이 글자가 늘어나는 걸
 * 알아볼 수 있는 속도로 띄워요.
 */
const FRAME_INTERVAL = 80;

const encoder = new TextEncoder();

/** SSE 프레임 한 개. 빈 줄이 이벤트의 끝을 알려요 */
const toFrame = (event: string, data: object) =>
  `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;

/** mock 응답을 서버가 보내는 순서대로 프레임으로 바꿔요 */
const toAnswerFrames = () => {
  const {
    conversationId,
    questionMessageId,
    answerMessageId,
    stages,
    deltas,
    evidences,
  } = searchAnswerStreamResponse;

  return [
    toFrame("accepted", { conversationId, questionMessageId, answerMessageId }),
    ...stages.map((stage) => toFrame("progress", { stage })),
    ...deltas.map((text) => toFrame("delta", { answerMessageId, text })),
    toFrame("evidence", { answerMessageId, items: evidences }),
    toFrame("completed", { answerMessageId, status: "COMPLETED" }),
  ];
};

/** 프레임을 FRAME_INTERVAL 간격으로 하나씩 흘려보내고 닫는 스트림이에요 */
const toDelayedStream = (frames: string[]) =>
  new ReadableStream({
    async start(controller) {
      for (const frame of frames) {
        await delay(FRAME_INTERVAL);
        controller.enqueue(encoder.encode(frame));
      }

      controller.close();
    },
  });

export const workspaceSearchConversationsHandlers = [
  http.post(
    "*/api/v1/workspaces/:workspaceId/search/conversations",
    () =>
      new HttpResponse(toDelayedStream(toAnswerFrames()), {
        headers: { "Content-Type": "text/event-stream" },
      }),
  ),
];
