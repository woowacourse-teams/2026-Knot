/**
 * SSE 프레임 하나를 파싱한 결과.
 *
 * 어느 API의 이벤트인지는 모르므로 `data`는 파싱하지 않은 문자열 그대로 둡니다.
 * 무엇이 유효한 이벤트 이름인지는 SSE가 아니라 엔드포인트 스펙이 정하므로 `fetch/`가 판단합니다.
 */
export interface SseEvent {
  /** 이벤트 이름. `event:` 줄이 없으면 SSE 명세 기본값인 "message" */
  event: string;
  /** `data:` 줄의 값. 여러 줄이면 줄바꿈으로 이어 붙임 */
  data: string;
}

/** 프레임(이벤트 하나)의 경계. SSE는 빈 줄로 이벤트를 끊어요 */
const FRAME_SEPARATOR = "\n\n";

/** `event:` 줄이 없을 때 쓰는 SSE 명세의 기본 이벤트 이름 */
const DEFAULT_EVENT_NAME = "message";

interface ParseSseEventsParams {
  /** 앞 청크에서 프레임을 완성하지 못하고 남은 문자열 */
  buffer: string;
  /** 이번에 읽은 청크 */
  chunk: string;
}

/**
 * 프레임 하나를 `event` / `data`로 읽습니다.
 *
 * `data:` 줄이 하나도 없는 프레임(주석·하트비트)은 전달할 내용이 없으므로 이벤트로 세지 않습니다.
 */
const toSseEvent = (frame: string) => {
  const dataLines: string[] = [];
  let event = DEFAULT_EVENT_NAME;

  for (const line of frame.split("\n")) {
    if (line.startsWith(":")) continue; // 주석 줄

    const colonIndex = line.indexOf(":");
    const field = colonIndex === -1 ? line : line.slice(0, colonIndex);
    // 필드 구분자 뒤의 공백 한 칸은 값이 아니라 서식이에요
    const value =
      colonIndex === -1 ? "" : line.slice(colonIndex + 1).replace(/^ /, "");

    if (field === "event") event = value;
    if (field === "data") dataLines.push(value);
  }

  if (dataLines.length === 0) return null;

  return { event, data: dataLines.join("\n") } satisfies SseEvent;
};

/**
 * @description 남은 버퍼와 새 청크를 이어 붙여 완성된 SSE 이벤트 목록과 다음 버퍼를 만듭니다
 * @param params - 앞 청크에서 남은 버퍼와 이번에 읽은 청크
 * @returns 완성된 이벤트 목록과, 아직 프레임을 이루지 못해 다음 청크로 넘길 버퍼
 * @example
 * const { events, buffer } = parseSseEvents({ buffer: "", chunk: 'event: chunk\ndata: {"delta":"안녕"}\n\n' });
 * // events: [{ event: "chunk", data: '{"delta":"안녕"}' }], buffer: ""
 */
export const parseSseEvents = ({ buffer, chunk }: ParseSseEventsParams) => {
  // 청크 경계에 \r과 \n이 나뉘어 걸릴 수 있으므로 이어 붙인 뒤에 한 번에 정규화해요
  const text = `${buffer}${chunk}`.replace(/\r\n/g, "\n");

  const frames = text.split(FRAME_SEPARATOR);
  // 마지막 조각은 빈 줄을 아직 못 만난 부분이라 다음 청크를 기다려요
  const rest = frames.pop() ?? "";

  return {
    events: frames.map(toSseEvent).filter((event) => event !== null),
    buffer: rest,
  };
};
