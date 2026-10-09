export interface SseEvent {
  /** 이벤트 이름. 어떤 이름이 오는지는 API 명세가 정해요 (예: delta, completed) */
  event: string;
  /** data 줄의 값 원문. JSON 파싱은 이벤트를 받아 쓰는 쪽에서 해요 */
  data: string;
}

interface ParseSseEventsParams {
  /** 직전 호출이 돌려준 buffer. 첫 호출에는 빈 문자열 */
  buffer: string;
  /** 이번에 스트림에서 읽어 글자로 바꾼 조각 */
  chunk: string;
}

/** 빈 줄로 끝난 이벤트 문자열 하나를 줄 단위로 읽어 SseEvent로 바꿔요 */
const toSseEvent = (rawEvent: string) => {
  let event = "message";
  const dataLines: string[] = [];

  rawEvent.split("\n").forEach((line) => {
    // 첫 콜론만 경계로 써야 data의 JSON 안 콜론이 값에 남아요
    const [field] = line.split(":", 1);
    const value = line.slice(field.length + 1).replace(/^ /, "");

    if (field === "event") event = value;
    if (field === "data") dataLines.push(value);
  });

  // 주석만 있는 keep-alive처럼 data가 없으면 이벤트가 아니에요
  if (dataLines.length === 0) return null;

  return { event, data: dataLines.join("\n") };
};

/**
 * @description SSE 응답 조각을 빈 줄(`\n\n`) 기준으로 잘라, 완성된 이벤트와 남은 끝부분을 돌려줘요
 * @param buffer 직전 호출이 돌려준 미완성 끝부분. 첫 호출에는 ""
 * @param chunk 스트림에서 이번에 읽은 문자열. 이벤트 중간에서도 잘려 와요
 * @returns events는 빈 줄까지 온 이벤트들, buffer는 다음 호출에 그대로 넘길 미완성 끝부분
 * @example
 * parseSseEvents({ buffer: 'event: delta\ndata: {"text":"안', chunk: '녕"}\n\nevent: compl' });
 * // { events: [{ event: "delta", data: '{"text":"안녕"}' }], buffer: "event: compl" }
 */
export const parseSseEvents = ({ buffer, chunk }: ParseSseEventsParams) => {
  // 붙인 뒤에 통일해야 조각 경계에서 갈라진 \r\n도 잡혀요
  const rawEvents = (buffer + chunk).replace(/\r\n/g, "\n").split("\n\n");

  // 마지막 조각은 빈 줄이 아직 안 온 미완성 이벤트예요
  const restBuffer = rawEvents.pop() ?? "";

  const events = rawEvents
    .map(toSseEvent)
    .filter((sseEvent) => sseEvent !== null);

  return { events, buffer: restBuffer };
};
