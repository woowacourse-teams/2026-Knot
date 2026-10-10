import { parseSseEvents } from "@api/sse/parseSseEvents";

interface ReadSseStreamParams {
  /** SSE 응답 본문. fetch 응답의 `response.body` */
  stream: ReadableStream<Uint8Array>;
  /** 요청에 넘긴 취소 신호. abort되면 남은 이벤트를 내지 않고 끝나요 */
  signal?: AbortSignal;
}

/**
 * @description SSE 응답 본문을 끝까지 읽으며 완성된 이벤트를 하나씩 내보내요
 * @param stream SSE 응답 본문
 * @param signal 요청에 넘긴 취소 신호. abort되면 오류 없이 끝나요
 * @returns 도착 순서대로 SseEvent를 내는 async generator. 받는 쪽이 중간에 멈추면 스트림 읽기를 취소해요
 * @example
 * for await (const { event, data } of readSseStream({ stream: response.body, signal })) {
 *   if (event === "delta") answer += JSON.parse(data).text;
 * }
 */
export async function* readSseStream({ stream, signal }: ReadSseStreamParams) {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  try {
    while (!signal?.aborted) {
      const { done, value } = await reader.read();

      // 끝났을 때는 마지막 이벤트가 빈 줄 없이 끝났을 수 있어 빈 줄을 붙여 남은 buffer까지 비워요.
      // 읽는 중에는 stream: true여야 한글처럼 여러 바이트인 글자가 조각 경계에서 깨지지 않아요
      const chunk = done
        ? `${decoder.decode()}\n\n`
        : decoder.decode(value, { stream: true });

      const parsed = parseSseEvents({ buffer, chunk });
      buffer = parsed.buffer;

      for (const event of parsed.events) {
        // 받는 쪽이 이벤트를 받다가 취소할 수 있어 하나 낼 때마다 다시 확인해요
        if (signal?.aborted) return;

        yield event;
      }

      if (done) return;
    }
  } catch (error) {
    // 취소는 사용자가 그만둔 것이라 실패로 보고하지 않고 조용히 끝내요
    if (!signal?.aborted) throw error;
  } finally {
    // 받는 쪽이 중간에 멈춰도 연결을 놓아주도록 읽기를 취소해요.
    // 취소된 스트림은 cancel()이 끝나지 않을 수 있어 기다리지 않고 실패도 무시해요
    void reader.cancel().catch(() => undefined);
  }
}
