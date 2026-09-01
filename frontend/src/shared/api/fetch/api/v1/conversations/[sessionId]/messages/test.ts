import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { chatMessageStreamResponse } from "@api/mock/responses/chatMessage";
import { mockServer } from "@api/mock/server";

import { ChatStreamRequestError, streamChatMessageApi } from ".";

const SESSION_ID = 100;
const SEND_CHAT_MESSAGE_PATH = "*/api/v1/conversations/:sessionId/messages";

const QUESTION = { content: "프로젝트 문서를 요약해줘" };

/** 스트림이 끝날 때까지 받아 이벤트를 순서대로 모읍니다 */
const collectStream = async (signal?: AbortSignal) => {
  const events = [];

  for await (const event of streamChatMessageApi({
    sessionId: SESSION_ID,
    body: QUESTION,
    signal,
  })) {
    events.push(event);
  }

  return events;
};

/** 서버가 보내는 SSE 프레임 한 개 */
const toFrame = (event: string, data: object) =>
  `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;

/** 준비된 바이트를 그대로 흘려보내는 SSE 응답 */
const sseResponse = (chunks: Uint8Array[]) =>
  new HttpResponse(
    new ReadableStream({
      start(controller) {
        chunks.forEach((chunk) => controller.enqueue(chunk));
        controller.close();
      },
    }),
    { headers: { "Content-Type": "text/event-stream" } },
  );

describe("streamChatMessageApi", () => {
  it("mock 스트림의 delta를 순서대로 내고 complete로 메시지 ID를 알려준다", async () => {
    const { deltas, messageId } = chatMessageStreamResponse;

    const events = await collectStream();

    expect(events).toEqual([
      ...deltas.map((delta) => ({ event: "chunk", data: { delta } })),
      { event: "complete", data: { messageId } },
    ]);
  });

  it("연결 전 409는 상태 코드와 오류 코드를 담아 throw한다", async () => {
    mockServer.use(
      http.post(SEND_CHAT_MESSAGE_PATH, () =>
        HttpResponse.json(
          {
            code: "CHAT_STREAM_ALREADY_ACTIVE",
            message: "이미 응답을 만들고 있어요",
          },
          { status: 409 },
        ),
      ),
    );

    await expect(collectStream()).rejects.toThrow(ChatStreamRequestError);
    await expect(collectStream()).rejects.toMatchObject({
      status: 409,
      code: "CHAT_STREAM_ALREADY_ACTIVE",
    });
  });

  it("연결 전 403은 throw한다", async () => {
    mockServer.use(
      http.post(SEND_CHAT_MESSAGE_PATH, () =>
        HttpResponse.json(
          { code: "CHAT_ACCESS_DENIED", message: "접근 권한이 없어요" },
          { status: 403 },
        ),
      ),
    );

    await expect(collectStream()).rejects.toMatchObject({
      status: 403,
      code: "CHAT_ACCESS_DENIED",
    });
  });

  it("연결 후 error 이벤트는 throw하지 않고 앞선 조각과 함께 흘려보낸다", async () => {
    const encoder = new TextEncoder();
    mockServer.use(
      http.post(SEND_CHAT_MESSAGE_PATH, () =>
        sseResponse([
          encoder.encode(toFrame("chunk", { delta: "테스트 " })),
          encoder.encode(
            toFrame("error", {
              code: "LLM_STREAM_FAILED",
              message: "답변 생성에 실패했습니다",
            }),
          ),
        ]),
      ),
    );

    await expect(collectStream()).resolves.toEqual([
      { event: "chunk", data: { delta: "테스트 " } },
      {
        event: "error",
        data: {
          code: "LLM_STREAM_FAILED",
          message: "답변 생성에 실패했습니다",
        },
      },
    ]);
  });

  it("모르는 이벤트 이름은 버린다", async () => {
    const encoder = new TextEncoder();
    mockServer.use(
      http.post(SEND_CHAT_MESSAGE_PATH, () =>
        sseResponse([
          encoder.encode(toFrame("heartbeat", {})),
          encoder.encode(toFrame("complete", { messageId: 102 })),
        ]),
      ),
    );

    await expect(collectStream()).resolves.toEqual([
      { event: "complete", data: { messageId: 102 } },
    ]);
  });

  it("한글이 청크 경계에서 잘려도 깨지지 않는다", async () => {
    const encoder = new TextEncoder();
    const frame = encoder.encode(toFrame("chunk", { delta: "테스트 " }));
    // "테"의 3바이트 한가운데를 지나도록 잘라요
    const splitIndex =
      encoder.encode('event: chunk\ndata: {"delta":"').length + 1;

    mockServer.use(
      http.post(SEND_CHAT_MESSAGE_PATH, () =>
        sseResponse([frame.slice(0, splitIndex), frame.slice(splitIndex)]),
      ),
    );

    await expect(collectStream()).resolves.toEqual([
      { event: "chunk", data: { delta: "테스트 " } },
    ]);
  });

  it("취소하면 그 뒤로는 아무 이벤트도 내지 않고 정상 종료한다", async () => {
    const controller = new AbortController();
    const events = [];

    for await (const event of streamChatMessageApi({
      sessionId: SESSION_ID,
      body: QUESTION,
      signal: controller.signal,
    })) {
      events.push(event);
      controller.abort();
    }

    expect(events).toHaveLength(1);
  });
});
