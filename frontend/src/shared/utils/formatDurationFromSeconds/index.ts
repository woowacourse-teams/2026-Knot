const SECONDS_PER_MINUTE = 60;
const MINUTES_PER_HOUR = 60;

/**
 * 녹음 길이(초)를 화면에 보여줄 문구로 바꿔요.
 *
 * 남는 초는 버려요(내림). 25분 0초부터 25분 59초까지는 모두 `25분`이에요.
 * 다만 1분이 안 되는 녹음이 `0분`으로 보이지 않도록, 1초부터 59초까지는 `1분`으로 보여줘요.
 *
 * 60분이 되면 `1시간`으로 넘어가고, 정각이면 남은 초가 있어도 뒤에 분을 붙이지 않아요.
 * 1시간 12분 0초부터 1시간 12분 59초까지는 `1시간 12분`이에요.
 * `녹음` 같은 당연한 말은 넣지 않고 길이만 씁니다.
 *
 * 0, 음수, `NaN`, `Infinity`처럼 유한한 양수가 아니면 `0분`을 돌려줘요.
 * 브라우저가 녹음 길이를 아직 모를 때 `NaN`이나 `Infinity`를 주기도 해서, 그대로 화면에 찍히지 않게 막아요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2062-7288 Chip/Duration}
 */
export const formatDurationFromSeconds = (durationSeconds: number) => {
  if (!Number.isFinite(durationSeconds) || durationSeconds <= 0) return "0분";

  // 내림하면 1분이 안 되는 녹음이 0분이 되므로 최소 1분을 보장해요
  const totalMinutes = Math.max(
    1,
    Math.floor(durationSeconds / SECONDS_PER_MINUTE),
  );

  if (totalMinutes < MINUTES_PER_HOUR) return `${totalMinutes}분`;

  const hours = Math.floor(totalMinutes / MINUTES_PER_HOUR);
  const minutes = totalMinutes % MINUTES_PER_HOUR;

  if (minutes === 0) return `${hours}시간`;

  return `${hours}시간 ${minutes}분`;
};
