const MS_PER_SECOND = 1000;

/**
 * 쌓아 둔 시간과 마지막으로 재개한 시각으로 지금까지 흐른 시간(초)을 구해요.
 *
 * 멈춘 상태(`resumedAt`이 `null`)면 쌓아 둔 시간만 봐요. 방금 시작|재개해 `now`가 아직
 * 이전 시각이면 그 구간은 0으로 봐요.
 *
 * @example
 * calculateElapsedSeconds(3000, null, 10_000) // 3
 * calculateElapsedSeconds(3000, 10_000, 12_500) // 5
 */
export const calculateElapsedSeconds = (
  accumulatedMs: number,
  resumedAt: number | null,
  now: number,
) => {
  const runningMs = resumedAt === null ? 0 : Math.max(0, now - resumedAt);

  return Math.floor((accumulatedMs + runningMs) / MS_PER_SECOND);
};
