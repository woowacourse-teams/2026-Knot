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
  const sseEvent: SseEvent = { event: "", data: "" };

  rawEvent.split("\n").forEach((line) => {
    // 첫 번째 콜론만 이름과 값의 경계예요. data의 JSON 안에 있는 콜론은 값에 남아야 해요
    const [field] = line.split(":", 1);
    // SSE 명세상 콜론 뒤 공백은 하나만 구분자이고, 나머지 공백은 값에 포함돼요
    const value = line.slice(field.length + 1).replace(/^ /, "");

    if (field === "event") sseEvent.event = value;
    if (field === "data") sseEvent.data = value;
  });

  return sseEvent;
};

/**
 * SSE 응답 조각을 이벤트 단위로 잘라 돌려줘요.
 *
 * 용어
 * - 이벤트: 서버가 보내는 메시지 한 개. `event: 이름` 줄과 `data: 값` 줄로 이뤄져요
 * - 빈 줄(`\n\n`): 이벤트 하나가 끝났다는 표시. 서버는 이벤트마다 끝에 붙여요
 * - chunk: 스트림에서 한 번 읽은 문자열. 네트워크가 정한 크기라 이벤트 중간에서도 잘려요
 * - buffer: 빈 줄을 아직 못 만나 이벤트로 바꾸지 못한 끝부분
 *
 * chunk만 보고 해석하면 `data: {"text":"안` 처럼 잘린 이벤트가 깨진 채로 나가요.
 * 그래서 빈 줄 앞까지만 이벤트로 바꾸고, 끝부분은 `buffer`로 돌려줘 다음 chunk와 이어 붙여요.
 * 호출하는 쪽은 돌려받은 `buffer`를 보관했다가 다음 호출에 그대로 넘겨야 해요.
 *
 * @example
 * // 1번째: 빈 줄이 없어 이벤트 없음, 받은 것 전부 보관
 * parseSseEvents({ buffer: "", chunk: 'event: delta\ndata: {"text":"안' });
 * // { events: [], buffer: 'event: delta\ndata: {"text":"안' }
 *
 * // 2번째: 보관한 것과 이어 붙여 이벤트 완성, 뒤에 잘린 부분은 다시 보관
 * parseSseEvents({ buffer: 'event: delta\ndata: {"text":"안', chunk: '녕"}\n\nevent: compl' });
 * // { events: [{ event: "delta", data: '{"text":"안녕"}' }], buffer: "event: compl" }
 */
export const parseSseEvents = ({ buffer, chunk }: ParseSseEventsParams) => {
  const rawEvents = (buffer + chunk).split("\n\n");

  // 마지막 조각은 빈 줄이 아직 오지 않은 미완성 이벤트라 다음 조각을 기다려요.
  // 빈 줄로 딱 끝났다면 마지막 조각은 ""라서 buffer도 비어요
  const restBuffer = rawEvents.pop() ?? "";

  return { events: rawEvents.map(toSseEvent), buffer: restBuffer };
};
