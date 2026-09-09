import type { KnotDesktopApi, LlmStreamError } from "@/shared/types/desktop";

/**
 * 데스크톱 셸의 구독 스트림(`window.knotDesktop.llm.streamAnswer`)을 Promise로 감싸는 곳.
 *
 * 셸은 콜백 셋(`chunk`·`complete`·`error`)과 취소 함수를 주는데, 전송 뮤테이션은 서버 SSE 경로처럼
 * "끝났는가, 실패했는가"를 한 번에 알고 싶어요. 그래서 조각은 그대로 흘리고 결과만 값으로 돌려줍니다.
 *
 * 폴백 판단은 여기서 하지 않아요. `fallback`이 true인 실패를 받은 뮤테이션이 같은 질문을 서버 SSE로
 * 다시 보냅니다(기획서 6.5 흐름 5, 로드맵 Q66).
 *
 * @see docs/electron-desktop-app-tech-plan.md 6.5 앱 안 구독 탐색
 */

/** 셸이 노출하는 구독 API. 브라우저에는 없어요 */
export type DesktopLlmApi = NonNullable<KnotDesktopApi["llm"]>;

/** 스트림이 끝난 방식 */
export type DesktopAnswerOutcome =
  | { kind: "complete"; messageId: number }
  | ({ kind: "error" } & LlmStreamError)
  | { kind: "cancelled" };

interface StreamDesktopAnswerParams {
  llm: DesktopLlmApi;
  workspaceId: number;
  sessionId: number;
  content: string;
  /** 조각이 도착할 때마다 불려요. 누적은 부르는 쪽이 해요 */
  onChunk: (delta: string) => void;
  /** 화면을 떠나거나 다른 대화로 옮길 때 스트림을 끊는 신호 */
  signal?: AbortSignal;
}

/**
 * @description 데스크톱 셸의 구독 스트림으로 질문을 보내고, 끝난 방식(완료·실패·취소)을 값으로 돌려줍니다
 * @param params - 셸 구독 API, 워크스페이스·세션 ID, 질문, 조각 콜백, 취소용 AbortSignal
 * @returns 완료면 저장된 답변 메시지 ID, 실패면 코드·문구·폴백 여부, 취소면 취소 표시
 * @example
 * const outcome = await streamDesktopAnswer({ llm, workspaceId: 7, sessionId: 42, content, onChunk });
 * if (outcome.kind === "error" && outcome.fallback) { /* 서버 SSE로 다시 보내기 *\/ }
 */
export const streamDesktopAnswer = ({
  llm,
  workspaceId,
  sessionId,
  content,
  onChunk,
  signal,
}: StreamDesktopAnswerParams) =>
  new Promise<DesktopAnswerOutcome>((resolve) => {
    if (signal?.aborted) {
      resolve({ kind: "cancelled" });
      return;
    }

    let settled = false;
    const settle = (outcome: DesktopAnswerOutcome) => {
      if (settled) return;
      settled = true;
      signal?.removeEventListener("abort", handleAbort);
      resolve(outcome);
    };

    const cancel = llm.streamAnswer(
      {
        workspaceId: String(workspaceId),
        sessionId: String(sessionId),
        content,
      },
      {
        chunk: (delta) => {
          // 취소한 뒤에 늦게 온 조각은 화면에 쌓지 않아요
          if (!settled) onChunk(delta);
        },
        complete: ({ messageId }) => settle({ kind: "complete", messageId }),
        error: (error) => settle({ kind: "error", ...error }),
      },
    );

    function handleAbort() {
      cancel();
      settle({ kind: "cancelled" });
    }

    signal?.addEventListener("abort", handleAbort, { once: true });
  });
