import { describe, expect, it } from "vitest";
import { createSseParser } from "../src/main/llm/sseParser";

describe("createSseParser", () => {
  it("빈 줄로 끝난 프레임만 내고 나머지는 버퍼에 둔다", () => {
    const parser = createSseParser();

    expect(parser.feed('event: a\ndata: {"x":1}\n\nevent: b\ndata: {"y"')).toEqual([{ event: "a", data: '{"x":1}' }]);
    expect(parser.feed(':2}\n\n')).toEqual([{ event: "b", data: '{"y":2}' }]);
  });

  it("event 줄이 없으면 message, data 여러 줄은 줄바꿈으로 잇고, 주석·핑 프레임은 내지 않는다", () => {
    const parser = createSseParser();

    expect(parser.feed("data: 1\ndata: 2\n\n: ping\n\nevent: only\n\n")).toEqual([{ event: "message", data: "1\n2" }]);
  });

  it("\\r\\n 줄바꿈도 청크 경계에 걸려도 읽는다", () => {
    const parser = createSseParser();

    expect(parser.feed("event: a\r")).toEqual([]);
    expect(parser.feed("\ndata: x\r\n\r\n")).toEqual([{ event: "a", data: "x" }]);
  });

  it("flush는 빈 줄 없이 남은 마지막 프레임을 낸다", () => {
    const parser = createSseParser();

    expect(parser.feed("event: a\ndata: tail")).toEqual([]);
    expect(parser.flush()).toEqual([{ event: "a", data: "tail" }]);
    expect(parser.flush()).toEqual([]);
  });
});
