/** 테스트용: 문자열 조각들을 SSE 본문으로 흘리는 fetch 응답 */
export function sseResponse(chunks: string[], init: { status?: number; headers?: Record<string, string> } = {}): Response {
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk));
      controller.close();
    },
  });
  return new Response(body, {
    status: init.status ?? 200,
    headers: { "content-type": "text/event-stream", ...init.headers },
  });
}

/** 조각을 모두 모은다 */
export async function collect(stream: AsyncGenerator<string>): Promise<string[]> {
  const deltas: string[] = [];
  for await (const delta of stream) deltas.push(delta);
  return deltas;
}
