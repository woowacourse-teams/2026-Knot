import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import {
  SearchStreamAcceptedDto,
  SearchStreamCompletedDto,
  SearchStreamDeltaDto,
  SearchStreamEvidenceDto,
  SearchStreamProgressDto,
} from "@api/dto/search";
import { csrfTokenResponse } from "@api/mock/responses/auth";
import { searchQuestionAnswerStreamResponse } from "@api/mock/responses/search";
import { mockServer } from "@api/mock/server";
import { SseRequestError } from "@api/sse/requestSseStream";

import { createSearchQuestionApi } from ".";

const WORKSPACE_ID = 1;
// 첫 질문 mock의 대화 ID와 다른 값이어야 경로 값을 그대로 쓰는지 드러나요
const CONVERSATION_ID = 200;
const SEARCH_QUESTIONS_PATH =
  "*/api/v1/workspaces/:workspaceId/search/conversations/:conversationId/questions";

const QUESTION = {
  content: "그럼 초기 스키마는 누가 정리했어?",
  requestId: "9b2e6f1a-3c4d-4e5f-8a7b-1c2d3e4f5a6b",
};

/** 스트림이 끝날 때까지 받아 이벤트를 순서대로 모아요 */
const readAllQuestionEvents = async () => {
  const events = [];

  for await (const event of createSearchQuestionApi({
    workspaceId: WORKSPACE_ID,
    conversationId: CONVERSATION_ID,
    body: QUESTION,
  })) {
    events.push(event);
  }

  return events;
};

/** 스트림을 끝까지 받다가 throw된 오류를 돌려줘요 */
const catchQuestionError = () =>
  readAllQuestionEvents().then(
    () => undefined,
    (error: unknown) => error,
  );

/** 이번 테스트에서만 accepted 전 HTTP 오류로 응답하게 바꿔요 */
const respondWithError = (status: number, code: string) =>
  mockServer.use(
    http.post(SEARCH_QUESTIONS_PATH, () =>
      HttpResponse.json({ code, message: "요청을 처리하지 못했어요" }, { status }),
    ),
  );

describe("createSearchQuestionApi", () => {
  // SEARCH-R1 문서 근거 답변
  it("[SEARCH-R1] 경로의 대화로 후속 질문을 보내고 accepted → progress → delta → evidence → completed 순서로 낸다", async () => {
    const { questionMessageId, answerMessageId, stages, deltas, evidences } =
      searchQuestionAnswerStreamResponse;

    const events = await readAllQuestionEvents();

    expect(events).toEqual([
      {
        event: "accepted",
        data: new SearchStreamAcceptedDto({
          conversationId: CONVERSATION_ID,
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

  it("질문 본문을 SSE와 CSRF 헤더와 함께 POST로 보낸다", async () => {
    const requests: Request[] = [];
    // 요청만 남기고 아무것도 돌려주지 않아 기본 mock 핸들러가 이어서 응답해요
    mockServer.use(
      http.post(SEARCH_QUESTIONS_PATH, ({ request }) => {
        requests.push(request.clone());
      }),
    );

    await readAllQuestionEvents();

    const [request] = requests;
    expect(new URL(request.url).pathname).toBe(
      `/api/v1/workspaces/${WORKSPACE_ID}/search/conversations/${CONVERSATION_ID}/questions`,
    );
    expect(request.method).toBe("POST");
    expect(request.headers.get("Accept")).toBe("text/event-stream");
    expect(request.headers.get("X-XSRF-TOKEN")).toBe(csrfTokenResponse.token);
    expect(await request.json()).toEqual(QUESTION);
  });

  it("모르는 이벤트 이름은 버린다", async () => {
    const accepted = {
      conversationId: CONVERSATION_ID,
      questionMessageId: 1,
      answerMessageId: 2,
    };
    mockServer.use(
      http.post(
        SEARCH_QUESTIONS_PATH,
        () =>
          new HttpResponse(
            `event: accepted\ndata: ${JSON.stringify(accepted)}\n\nevent: heartbeat\ndata: {}\n\n`,
            { headers: { "Content-Type": "text/event-stream" } },
          ),
      ),
    );

    const events = await readAllQuestionEvents();

    expect(events).toEqual([
      { event: "accepted", data: new SearchStreamAcceptedDto(accepted) },
    ]);
  });

  // SEARCH-R6 409 중복 거절
  it("[SEARCH-R6] accepted 전 409는 상태 코드와 서버 오류 코드를 담아 throw한다", async () => {
    respondWithError(409, "SEARCH_REQUEST_CONFLICT");

    const error = await catchQuestionError();

    expect(error).toBeInstanceOf(SseRequestError);
    expect(error).toMatchObject({ status: 409, code: "SEARCH_REQUEST_CONFLICT" });
  });

  // API 명세에 대화가 없을 때의 응답이 정해지지 않아, 404가 오면 상태 코드와 서버 오류 코드가 그대로 담기는지만 확인해요
  it("accepted 전 404는 상태 코드와 서버 오류 코드를 담아 throw한다", async () => {
    respondWithError(404, "SEARCH_CONVERSATION_NOT_FOUND");

    const error = await catchQuestionError();

    expect(error).toBeInstanceOf(SseRequestError);
    expect(error).toMatchObject({
      status: 404,
      code: "SEARCH_CONVERSATION_NOT_FOUND",
    });
  });
});
