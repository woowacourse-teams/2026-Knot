import { describe, expect, it } from "vitest";

import { parseSseEvents } from ".";

describe("parseSseEvents", () => {
  it("빈 줄로 끝난 온전한 프레임 하나를 이벤트로 돌려준다", () => {
    const { events, buffer } = parseSseEvents({
      buffer: "",
      chunk: 'event: chunk\ndata: {"delta":"테스트 "}\n\n',
    });

    expect(events).toEqual([{ event: "chunk", data: '{"delta":"테스트 "}' }]);
    expect(buffer).toBe("");
  });

  it("한 청크에 프레임이 여러 개면 순서대로 모두 돌려준다", () => {
    const { events, buffer } = parseSseEvents({
      buffer: "",
      chunk:
        'event: chunk\ndata: {"delta":"테스트 "}\n\n' +
        'event: complete\ndata: {"messageId":102}\n\n',
    });

    expect(events).toEqual([
      { event: "chunk", data: '{"delta":"테스트 "}' },
      { event: "complete", data: '{"messageId":102}' },
    ]);
    expect(buffer).toBe("");
  });

  it("data 중간에서 잘린 청크는 이벤트를 내지 않고 버퍼에 남긴다", () => {
    const { events, buffer } = parseSseEvents({
      buffer: "",
      chunk: 'event: chunk\ndata: {"delta":"테',
    });

    expect(events).toEqual([]);
    expect(buffer).toBe('event: chunk\ndata: {"delta":"테');
  });

  it("남은 버퍼에 다음 청크를 이어 붙여 잘렸던 프레임을 완성한다", () => {
    const { events, buffer } = parseSseEvents({
      buffer: 'event: chunk\ndata: {"delta":"테',
      chunk: '스트 "}\n\n',
    });

    expect(events).toEqual([{ event: "chunk", data: '{"delta":"테스트 "}' }]);
    expect(buffer).toBe("");
  });

  it("모르는 이벤트 이름도 버리지 않고 그대로 돌려준다", () => {
    const { events } = parseSseEvents({
      buffer: "",
      chunk: "event: heartbeat\ndata: {}\n\n",
    });

    expect(events).toEqual([{ event: "heartbeat", data: "{}" }]);
  });

  it("event 줄이 없으면 SSE 기본 이벤트 이름인 message로 채운다", () => {
    const { events } = parseSseEvents({ buffer: "", chunk: "data: hello\n\n" });

    expect(events).toEqual([{ event: "message", data: "hello" }]);
  });

  it("data 줄이 여러 개면 줄바꿈으로 이어 붙인다", () => {
    const { events } = parseSseEvents({
      buffer: "",
      chunk: "event: chunk\ndata: 첫 줄\ndata: 둘째 줄\n\n",
    });

    expect(events).toEqual([{ event: "chunk", data: "첫 줄\n둘째 줄" }]);
  });

  it("줄 끝이 CRLF여도 같은 이벤트로 읽는다", () => {
    const { events, buffer } = parseSseEvents({
      buffer: "",
      chunk: 'event: chunk\r\ndata: {"delta":"테스트 "}\r\n\r\n',
    });

    expect(events).toEqual([{ event: "chunk", data: '{"delta":"테스트 "}' }]);
    expect(buffer).toBe("");
  });

  it("주석 줄만 있는 프레임은 이벤트로 세지 않는다", () => {
    const { events, buffer } = parseSseEvents({
      buffer: "",
      chunk: ": keep-alive\n\n",
    });

    expect(events).toEqual([]);
    expect(buffer).toBe("");
  });
});
