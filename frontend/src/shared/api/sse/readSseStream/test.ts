import { describe, expect, it, vi } from "vitest";

import { readSseStream } from ".";

const encoder = new TextEncoder();

const toBytes = (chunk: string | Uint8Array) =>
  typeof chunk === "string" ? encoder.encode(chunk) : chunk;

/** 준비된 조각을 차례로 흘려보내고 닫히는 스트림이에요 */
const createClosedStream = (chunks: (string | Uint8Array)[]) =>
  new ReadableStream<Uint8Array>({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(toBytes(chunk)));
      controller.close();
    },
  });

/** 스트림이 끝날 때까지 받아 이벤트를 순서대로 모아요 */
const readAllEvents = async (stream: ReadableStream<Uint8Array>) => {
  const events = [];

  for await (const event of readSseStream({ stream })) events.push(event);

  return events;
};

describe("readSseStream", () => {
  it("두 조각에 걸쳐 잘린 이벤트를 남은 부분에 이어 붙여 낸다", async () => {
    const stream = createClosedStream([
      "event: delta\ndata: 안",
      "녕\n\nevent: done\ndata: 1\n\n",
    ]);

    const events = await readAllEvents(stream);

    expect(events).toEqual([
      { event: "delta", data: "안녕" },
      { event: "done", data: "1" },
    ]);
  });

  it("한글 바이트가 조각 경계에서 잘려도 깨지지 않는다", async () => {
    const bytes = encoder.encode("data: 안녕\n\n");
    // "안"의 3바이트 한가운데를 지나도록 잘라요
    const splitIndex = encoder.encode("data: ").length + 1;
    const stream = createClosedStream([
      bytes.slice(0, splitIndex),
      bytes.slice(splitIndex),
    ]);

    const events = await readAllEvents(stream);

    expect(events).toEqual([{ event: "message", data: "안녕" }]);
  });

  it("스트림이 빈 줄 없이 끝나도 마지막 이벤트를 낸다", async () => {
    const stream = createClosedStream(["event: done\ndata: 1"]);

    const events = await readAllEvents(stream);

    expect(events).toEqual([{ event: "done", data: "1" }]);
  });

  it("받는 쪽이 중간에 멈추면 스트림 읽기를 취소한다", async () => {
    const cancel = vi.fn();
    // 닫지 않은 스트림이라 취소하지 않으면 계속 다음 조각을 기다려요
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode("data: 1\n\ndata: 2\n\n"));
      },
      cancel,
    });

    for await (const event of readSseStream({ stream })) {
      if (event.data === "1") break;
    }

    await vi.waitFor(() => expect(cancel).toHaveBeenCalled());
  });

  it("signal을 abort하면 남은 이벤트를 내지 않고 정상 종료한다", async () => {
    const controller = new AbortController();
    const stream = createClosedStream(["data: 1\n\ndata: 2\n\ndata: 3\n\n"]);
    const events = [];

    for await (const event of readSseStream({
      stream,
      signal: controller.signal,
    })) {
      events.push(event);
      controller.abort();
    }

    expect(events).toEqual([{ event: "message", data: "1" }]);
  });
});
