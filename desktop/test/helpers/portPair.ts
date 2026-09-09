import type { PortLike } from "../../src/shared/agentProtocol";

/** Electron `MessageChannelMain`을 흉내 낸 메모리 포트 한 쌍. 메시지는 다음 틱에 반대쪽 리스너로 간다 */
export function createPortPair(): [PortLike, PortLike] {
  const listenersA: Array<(event: { data: unknown }) => void> = [];
  const listenersB: Array<(event: { data: unknown }) => void> = [];

  const make = (mine: typeof listenersA, theirs: typeof listenersB): PortLike => ({
    postMessage(message) {
      // MessagePort는 구조적 복제를 하므로 JSON 왕복으로 흉내 낸다
      const data: unknown = JSON.parse(JSON.stringify(message));
      setImmediate(() => {
        for (const listener of theirs) listener({ data });
      });
    },
    on(_event, listener) {
      mine.push(listener);
    },
    start() {},
  });

  return [make(listenersA, listenersB), make(listenersB, listenersA)];
}
