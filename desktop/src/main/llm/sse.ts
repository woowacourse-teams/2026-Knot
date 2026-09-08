/**
 * SSE(text/event-stream) 파서.
 *
 * 웹 SPA의 `parseSseEvents`와 같은 규칙이다 — 프레임은 빈 줄, 줄 끝은 `\r\n`·`\n`·`\r`
 * 모두 인정, `:`로 시작하는 줄은 주석, `data:`가 여러 줄이면 `\n`으로 잇는다.
 * OpenAI 호환(`data:`만)·Anthropic(`event:` + `data:`) 두 스트림을 이것 하나로 읽는다.
 */

export interface SseEvent {
  /** `event:` 필드. 없으면 "message" */
  event: string;
  data: string;
}

export interface SseParseResult {
  events: SseEvent[];
  /** 아직 프레임이 끝나지 않은 나머지. 다음 호출의 `buffer`로 넘긴다 */
  buffer: string;
}

/**
 * 이어 붙인 텍스트에서 완성된 프레임만 이벤트로 바꾼다.
 *
 * 끝이 `\r`이면 다음 조각이 `\n`으로 시작할 수 있어 그 한 글자는 처리하지 않고 남긴다.
 */
export function parseSseEvents(buffer: string, chunk: string): SseParseResult {
  let text = buffer + chunk;
  let holdback = "";
  if (text.endsWith("\r")) {
    holdback = "\r";
    text = text.slice(0, -1);
  }
  text = text.replace(/\r\n/g, "\n").replace(/\r/g, "\n");

  const frames = text.split("\n\n");
  const rest = frames.pop() ?? "";

  const events: SseEvent[] = [];
  for (const frame of frames) {
    const event = parseFrame(frame);
    if (event !== null) events.push(event);
  }
  return { events, buffer: rest + holdback };
}

function parseFrame(frame: string): SseEvent | null {
  let eventName = "";
  const dataLines: string[] = [];

  for (const line of frame.split("\n")) {
    if (line === "" || line.startsWith(":")) continue;

    const separator = line.indexOf(":");
    const field = separator < 0 ? line : line.slice(0, separator);
    let value = separator < 0 ? "" : line.slice(separator + 1);
    if (value.startsWith(" ")) value = value.slice(1);

    if (field === "event") eventName = value;
    else if (field === "data") dataLines.push(value);
    // id·retry·그 밖의 필드는 쓰지 않는다
  }

  if (dataLines.length === 0) return null;
  return { event: eventName === "" ? "message" : eventName, data: dataLines.join("\n") };
}

/**
 * fetch 응답 본문을 SSE 이벤트로 흘려보낸다.
 *
 * `TextDecoder(stream: true)`라 한글처럼 여러 바이트인 글자가 조각 경계에서 깨지지 않는다.
 * 소비자가 중간에 멈추면(`break`·throw) 리더를 취소해 연결을 놓아준다.
 */
export async function* readSseEvents(body: ReadableStream<Uint8Array>): AsyncGenerator<SseEvent> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        // 마지막 프레임이 빈 줄 없이 끝났을 수 있어 남은 버퍼를 한 번 더 비운다
        const flushed = parseSseEvents(buffer, "\n\n");
        buffer = flushed.buffer;
        yield* flushed.events;
        return;
      }
      const parsed = parseSseEvents(buffer, decoder.decode(value, { stream: true }));
      buffer = parsed.buffer;
      yield* parsed.events;
    }
  } finally {
    // 취소된 스트림은 cancel()이 끝나지 않을 수 있어 기다리지 않고 실패도 무시한다
    void reader.cancel().catch(() => undefined);
  }
}
