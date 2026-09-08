import { describe, expect, it } from "vitest";
import { parseSseEvents, readSseEvents } from "../src/main/llm/sse";

describe("parseSseEvents", () => {
  it("빈 줄로 끝난 프레임만 이벤트로 바꾸고 나머지는 버퍼에 남긴다", () => {
    const result = parseSseEvents("", 'data: {"a":1}\n\ndata: {"b"');

    expect(result.events).toEqual([{ event: "message", data: '{"a":1}' }]);
    expect(result.buffer).toBe('data: {"b"');
  });

  it("event 필드와 여러 줄 data를 읽는다", () => {
    const result = parseSseEvents("", "event: content_block_delta\ndata: {\"x\":\ndata: 1}\n\n");

    expect(result.events).toEqual([{ event: "content_block_delta", data: '{"x":\n1}' }]);
  });

  it("\\r\\n 줄 끝과 조각 경계에서 갈라진 \\r\\n을 모두 처리한다", () => {
    const first = parseSseEvents("", "data: one\r\n\r");
    expect(first.events).toEqual([]);

    const second = parseSseEvents(first.buffer, "\ndata: two\r\n\r\n");
    expect(second.events).toEqual([
      { event: "message", data: "one" },
      { event: "message", data: "two" },
    ]);
    expect(second.buffer).toBe("");
  });

  it("주석 줄과 data 없는 프레임은 버린다", () => {
    const result = parseSseEvents("", ": keep-alive\n\nevent: ping\n\ndata: [DONE]\n\n");

    expect(result.events).toEqual([{ event: "message", data: "[DONE]" }]);
  });

  it("값 앞의 공백 하나만 떼고 나머지는 보존한다", () => {
    const result = parseSseEvents("", "data:  두 칸\n\ndata:없음\n\n");

    expect(result.events.map((event) => event.data)).toEqual([" 두 칸", "없음"]);
  });
});

describe("readSseEvents", () => {
  function streamOf(chunks: Uint8Array[]): ReadableStream<Uint8Array> {
    return new ReadableStream<Uint8Array>({
      start(controller) {
        for (const chunk of chunks) controller.enqueue(chunk);
        controller.close();
      },
    });
  }

  it("여러 바이트 글자가 조각 경계에서 갈라져도 깨지지 않는다", async () => {
    const bytes = new TextEncoder().encode("data: 한글\n\n");
    const body = streamOf([bytes.slice(0, 7), bytes.slice(7)]);

    const events = [];
    for await (const event of readSseEvents(body)) events.push(event);

    expect(events).toEqual([{ event: "message", data: "한글" }]);
  });

  it("빈 줄 없이 끝난 마지막 프레임도 비운다", async () => {
    const body = streamOf([new TextEncoder().encode("data: 끝")]);

    const events = [];
    for await (const event of readSseEvents(body)) events.push(event);

    expect(events).toEqual([{ event: "message", data: "끝" }]);
  });
});
