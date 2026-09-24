const SECONDS_PER_MINUTE = 60;
const MINUTES_PER_HOUR = 60;

/**
 * 녹음 길이(초)를 화면에 보여줄 문구로 바꿔요.
 *
 * 남는 초는 다음 분으로 올려요. 25분 1초짜리 녹음은 `26분`이 돼요.
 * 녹음이 있다는 건 이미 말소리가 있었다는 뜻이라, 1분이 안 되는 길이도 `1분`으로 보여줘요.
 *
 * 올려서 60분이 되면 `1시간`으로 넘어가고, 정각이면 뒤에 분을 붙이지 않아요.
 * `녹음` 같은 당연한 말은 넣지 않고 길이만 씁니다.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2062-7288 Chip/Duration}
 */
export const formatDurationFromSeconds = (durationSeconds: number) => {
  const totalMinutes = Math.max(
    0,
    Math.ceil(durationSeconds / SECONDS_PER_MINUTE),
  );

  if (totalMinutes < MINUTES_PER_HOUR) return `${totalMinutes}분`;

  const hours = Math.floor(totalMinutes / MINUTES_PER_HOUR);
  const minutes = totalMinutes % MINUTES_PER_HOUR;

  if (minutes === 0) return `${hours}시간`;

  return `${hours}시간 ${minutes}분`;
};
