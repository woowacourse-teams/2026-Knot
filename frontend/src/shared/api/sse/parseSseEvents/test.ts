import { describe, expect, it } from "vitest";

import { parseSseEvents } from ".";

// 예시 값은 BE API 명세(탐색 질문 전송)의 이벤트 이름과 data 필드를 따라요
const HELLO = '{"answerMessageId":1,"text":"안녕"}';
const WORLD = '{"answerMessageId":1,"text":"하세요"}';

describe("parseSseEvents", () => {
  it("빈 줄로 끝난 이벤트 하나를 돌려준다", () => {
    expect(
      parseSseEvents({ buffer: "", chunk: `event: delta\ndata: ${HELLO}\n\n` }),
    ).toEqual({
      events: [{ event: "delta", data: HELLO }],
      buffer: "",
    });
  });

  it("한 조각에 이벤트가 여러 개면 순서대로 모두 돌려준다", () => {
    expect(
      parseSseEvents({
        buffer: "",
        chunk: `event: delta\ndata: ${HELLO}\n\nevent: delta\ndata: ${WORLD}\n\n`,
      }),
    ).toEqual({
      events: [
        { event: "delta", data: HELLO },
        { event: "delta", data: WORLD },
      ],
      buffer: "",
    });
  });

  it("빈 줄이 오기 전에 잘린 부분은 이벤트로 내지 않고 buffer로 남긴다", () => {
    expect(
      parseSseEvents({ buffer: "", chunk: 'event: delta\ndata: {"answerMessageId":1,"text":"안' }),
    ).toEqual({
      events: [],
      buffer: 'event: delta\ndata: {"answerMessageId":1,"text":"안',
    });
  });

  it("남은 buffer에 다음 조각을 이어 붙여 이벤트를 완성한다", () => {
    expect(
      parseSseEvents({ buffer: 'event: delta\ndata: {"answerMessageId":1,"text":"안', chunk: '녕"}\n\n' }),
    ).toEqual({
      events: [{ event: "delta", data: HELLO }],
      buffer: "",
    });
  });

  it("완성된 이벤트 뒤에 잘린 부분이 있으면 이벤트는 돌려주고 잘린 부분만 남긴다", () => {
    expect(
      parseSseEvents({
        buffer: "",
        chunk: `event: delta\ndata: ${HELLO}\n\nevent: compl`,
      }),
    ).toEqual({
      events: [{ event: "delta", data: HELLO }],
      buffer: "event: compl",
    });
  });
});
