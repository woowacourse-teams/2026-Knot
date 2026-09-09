/**
 * 증분 SSE(text/event-stream) 파서. Anthropic Messages API 스트림(지식 §6.4)을 프레임 단위로 자른다.
 *
 * 청크 경계가 프레임 중간에 걸릴 수 있으므로 남은 문자열을 버퍼에 두고 빈 줄(`\n\n`)을 만난 프레임만 낸다.
 * `data:` 줄이 없는 프레임(주석·핑)은 내지 않는다. `data`는 JSON을 풀지 않은 문자열 그대로다 —
 * 어떤 이벤트가 유효한지는 엔드포인트 계약이 정하므로 호출자(`messagesClient`)가 판단한다.
 * 웹 SPA의 `parseSseEvents`와 같은 규칙이다.
 */

export interface SseFrame {
  /** `event:` 줄. 없으면 SSE 명세 기본값 "message" */
  event: string;
  /** `data:` 줄. 여러 줄이면 `\n`으로 잇는다 */
  data: string;
}

const FRAME_SEPARATOR = "\n\n";
const DEFAULT_EVENT_NAME = "message";

export interface SseParser {
  /** 새 청크를 더해 완성된 프레임을 돌려준다 */
  feed(chunk: string): SseFrame[];
  /** 스트림이 끝났을 때 빈 줄 없이 남은 마지막 프레임을 낸다 */
  flush(): SseFrame[];
}

export function createSseParser(): SseParser {
  let buffer = "";

  return {
    feed(chunk) {
      const text = `${buffer}${chunk}`.replace(/\r\n/g, "\n");
      const frames = text.split(FRAME_SEPARATOR);
      buffer = frames.pop() ?? "";
      return frames.map(toFrame).filter((frame): frame is SseFrame => frame !== null);
    },
    flush() {
      const rest = buffer;
      buffer = "";
      const frame = toFrame(rest);
      return frame === null ? [] : [frame];
    },
  };
}

function toFrame(raw: string): SseFrame | null {
  const dataLines: string[] = [];
  let event = DEFAULT_EVENT_NAME;

  for (const line of raw.split("\n")) {
    if (line.startsWith(":")) continue;
    const colonIndex = line.indexOf(":");
    const field = colonIndex === -1 ? line : line.slice(0, colonIndex);
    // 구분자 뒤의 공백 한 칸은 값이 아니라 서식이다
    const value = colonIndex === -1 ? "" : line.slice(colonIndex + 1).replace(/^ /, "");
    if (field === "event") event = value;
    if (field === "data") dataLines.push(value);
  }

  if (dataLines.length === 0) return null;
  return { event, data: dataLines.join("\n") };
}
