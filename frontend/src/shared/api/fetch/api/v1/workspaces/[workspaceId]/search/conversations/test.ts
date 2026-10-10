import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";

import {
  SearchStreamAcceptedDto,
  SearchStreamCompletedDto,
  SearchStreamDeltaDto,
  SearchStreamEvidenceDto,
  SearchStreamExcludedDocumentsDto,
  SearchStreamFailedDto,
  SearchStreamProgressDto,
  SearchStreamStoppedDto,
} from "@api/dto/search";
import { searchAnswerStreamResponse } from "@api/mock/responses/search";
import { mockServer } from "@api/mock/server";
import { SseRequestError } from "@api/sse/requestSseStream";

import { createSearchConversationApi } from ".";

const WORKSPACE_ID = 1;
const SEARCH_CONVERSATIONS_PATH =
  "*/api/v1/workspaces/:workspaceId/search/conversations";

const QUESTION = {
  content: "DB 기술 선정 관련 문서 있어?",
  requestId: "4f1c2a52-7d1e-4a43-9f61-0c5b9b1d2e10",
};

const ACCEPTED = {
  conversationId: 100,
  questionMessageId: 1001,
  answerMessageId: 1002,
};
const ANSWER_MESSAGE_ID = ACCEPTED.answerMessageId;

const encoder = new TextEncoder();

/** 스트림이 끝날 때까지 받아 이벤트를 순서대로 모아요 */
const readAllSearchEvents = async () => {
  const events = [];

  for await (const event of createSearchConversationApi({
    workspaceId: WORKSPACE_ID,
    body: QUESTION,
  })) {
    events.push(event);
  }

  return events;
};

/** 스트림을 끝까지 받다가 throw된 오류를 돌려줘요 */
const catchSearchError = () =>
  readAllSearchEvents().then(
    () => undefined,
    (error: unknown) => error,
  );

/** 서버가 보내는 SSE 프레임 한 개예요 */
const toFrame = (event: string, data: object) =>
  `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;

const toBytes = (chunk: string | Uint8Array) =>
  typeof chunk === "string" ? encoder.encode(chunk) : chunk;

/** 이번 테스트에서만 준비된 조각을 흘려보내고 닫는 SSE 응답으로 바꿔요 */
const respondWithSseChunks = (chunks: (string | Uint8Array)[]) =>
  mockServer.use(
    http.post(
      SEARCH_CONVERSATIONS_PATH,
      () =>
        new HttpResponse(
          new ReadableStream({
            start(controller) {
              chunks.forEach((chunk) => controller.enqueue(toBytes(chunk)));
              controller.close();
            },
          }),
          { headers: { "Content-Type": "text/event-stream" } },
        ),
    ),
  );

/** 이번 테스트에서만 accepted 전 HTTP 오류로 응답하게 바꿔요 */
const respondWithError = (status: number, code: string) =>
  mockServer.use(
    http.post(SEARCH_CONVERSATIONS_PATH, () =>
      HttpResponse.json({ code, message: "요청을 처리하지 못했어요" }, { status }),
    ),
  );

describe("createSearchConversationApi", () => {
  // SEARCH-R1 문서 근거 답변
  it("[SEARCH-R1] mock 스트림을 accepted → progress → delta → evidence → completed 순서로 낸다", async () => {
    const {
      conversationId,
      questionMessageId,
      answerMessageId,
      stages,
      deltas,
      evidences,
    } = searchAnswerStreamResponse;

    const events = await readAllSearchEvents();

    expect(events).toEqual([
      {
        event: "accepted",
        data: new SearchStreamAcceptedDto({
          conversationId,
          questionMessageId,
          answerMessageId,
        }),
      },
      ...stages.map((stage) => ({
        event: "progress",
        data: new SearchStreamProgressDto({ stage }),
      })),
      ...deltas.map((text) => ({
        event: "delta",
        data: new SearchStreamDeltaDto({ answerMessageId, text }),
      })),
      {
        event: "evidence",
        data: new SearchStreamEvidenceDto({ answerMessageId, items: evidences }),
      },
      {
        event: "completed",
        data: new SearchStreamCompletedDto({
          answerMessageId,
          status: "COMPLETED",
        }),
      },
    ]);
  });

  // SEARCH-R6 409 중복 거절
  it("[SEARCH-R6] accepted 전 409는 상태 코드와 서버 오류 코드를 담아 throw한다", async () => {
    respondWithError(409, "SEARCH_REQUEST_CONFLICT");

    const error = await catchSearchError();

    expect(error).toBeInstanceOf(SseRequestError);
    expect(error).toMatchObject({ status: 409, code: "SEARCH_REQUEST_CONFLICT" });
  });

  // SEARCH-R4 accepted 전 실패
  it("[SEARCH-R4] accepted 전 403은 상태 코드와 서버 오류 코드를 담아 throw한다", async () => {
    respondWithError(403, "WORKSPACE_ACCESS_DENIED");

    const error = await catchSearchError();

    expect(error).toBeInstanceOf(SseRequestError);
    expect(error).toMatchObject({ status: 403, code: "WORKSPACE_ACCESS_DENIED" });
  });

  // SEARCH-R4 accepted 뒤의 실패는 HTTP 오류가 아니라 이벤트로 와요
  it("[SEARCH-R4] accepted 뒤 failed 이벤트는 throw하지 않고 그대로 낸다", async () => {
    const delta = { answerMessageId: ANSWER_MESSAGE_ID, text: "DB는 " };
    const failed = {
      answerMessageId: ANSWER_MESSAGE_ID,
      status: "FAILED",
      code: "LLM_STREAM_FAILED",
    } as const;
    respondWithSseChunks([
      toFrame("accepted", ACCEPTED),
      toFrame("delta", delta),
      toFrame("failed", failed),
    ]);

    const events = await readAllSearchEvents();

    expect(events).toEqual([
      { event: "accepted", data: new SearchStreamAcceptedDto(ACCEPTED) },
      { event: "delta", data: new SearchStreamDeltaDto(delta) },
      { event: "failed", data: new SearchStreamFailedDto(failed) },
    ]);
  });

  it("모르는 이벤트 이름은 버린다", async () => {
    respondWithSseChunks([
      toFrame("accepted", ACCEPTED),
      toFrame("heartbeat", {}),
      "data: {}\n\n",
    ]);

    const events = await readAllSearchEvents();

    expect(events).toEqual([
      { event: "accepted", data: new SearchStreamAcceptedDto(ACCEPTED) },
    ]);
  });

  it("한글이 청크 경계 바이트에서 잘려도 깨지지 않는다", async () => {
    const delta = { answerMessageId: ANSWER_MESSAGE_ID, text: "테스트 " };
    const frame = toFrame("delta", delta);
    const frameBytes = encoder.encode(frame);
    // "테"의 3바이트 한가운데를 지나도록 잘라요
    const splitIndex =
      encoder.encode(frame.slice(0, frame.indexOf("테"))).length + 1;
    respondWithSseChunks([
      frameBytes.slice(0, splitIndex),
      frameBytes.slice(splitIndex),
    ]);

    const events = await readAllSearchEvents();

    expect(events).toEqual([
      { event: "delta", data: new SearchStreamDeltaDto(delta) },
    ]);
  });

  // SEARCH-R2 근거 최대 3개
  it("[SEARCH-R2] progress·excluded_documents·evidence·stopped를 각각 받는다", async () => {
    const evidence = {
      answerMessageId: ANSWER_MESSAGE_ID,
      items: [1, 2, 3].map((rank) => ({
        documentId: 10 + rank,
        title: `회의록 ${rank}`,
        topic: "DB 기술 선정",
        rank,
      })),
    };
    const stopped = {
      answerMessageId: ANSWER_MESSAGE_ID,
      status: "STOPPED",
    } as const;
    respondWithSseChunks([
      toFrame("progress", { stage: "SEARCHING" }),
      toFrame("excluded_documents", { count: 2 }),
      toFrame("evidence", evidence),
      toFrame("stopped", stopped),
    ]);

    const events = await readAllSearchEvents();

    expect(events).toEqual([
      {
        event: "progress",
        data: new SearchStreamProgressDto({ stage: "SEARCHING" }),
      },
      {
        event: "excluded_documents",
        data: new SearchStreamExcludedDocumentsDto({ count: 2 }),
      },
      { event: "evidence", data: new SearchStreamEvidenceDto(evidence) },
      { event: "stopped", data: new SearchStreamStoppedDto(stopped) },
    ]);
  });

  // SEARCH-R8 연결 끊김
  it("[SEARCH-R8] signal을 abort하면 그 뒤로 이벤트 없이 정상 종료한다", async () => {
    const controller = new AbortController();
    const events = [];

    for await (const event of createSearchConversationApi({
      workspaceId: WORKSPACE_ID,
      body: QUESTION,
      signal: controller.signal,
    })) {
      events.push(event);
      controller.abort();
    }

    expect(events).toEqual([
      { event: "accepted", data: expect.any(SearchStreamAcceptedDto) },
    ]);
  });

  // SEARCH-R8 연결 끊김
  it("[SEARCH-R8] 받는 쪽이 completed에서 return하면 이후 프레임을 읽지 않고 스트림을 정리한다", async () => {
    // msw가 응답 본문을 옮겨 담아 원본 스트림의 cancel까지는 전달되지 않으므로 reader의 cancel을 지켜봐요
    const cancel = vi.spyOn(ReadableStreamDefaultReader.prototype, "cancel");
    const completed = {
      answerMessageId: ANSWER_MESSAGE_ID,
      status: "COMPLETED",
    } as const;
    mockServer.use(
      http.post(
        SEARCH_CONVERSATIONS_PATH,
        () =>
          new HttpResponse(
            // 닫지 않은 스트림이라 정리하지 않으면 계속 다음 프레임을 기다려요
            new ReadableStream({
              start(controller) {
                controller.enqueue(
                  encoder.encode(
                    toFrame("completed", completed) +
                      toFrame("delta", { answerMessageId: 0, text: "뒤" }),
                  ),
                );
              },
            }),
            { headers: { "Content-Type": "text/event-stream" } },
          ),
      ),
    );

    const receiveUntilCompleted = async () => {
      for await (const { event, data } of createSearchConversationApi({
        workspaceId: WORKSPACE_ID,
        body: QUESTION,
      })) {
        if (event === "completed") return data.answerMessageId;
      }
    };

    await expect(receiveUntilCompleted()).resolves.toBe(ANSWER_MESSAGE_ID);
    await vi.waitFor(() => expect(cancel).toHaveBeenCalled());
  });
});
