const SECONDS_PER_MINUTE = 60;
const SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE;

const pad = (value: number) => String(value).padStart(2, "0");

/**
 * 녹음한 시간(초)을 `분:초`로 바꿔요. 한 시간을 넘으면 `시:분:초`가 돼요.
 *
 * @example
 * formatRecordingTime(768) // "12:48"
 * formatRecordingTime(3723) // "1:02:03"
 */
export const formatRecordingTime = (totalSeconds: number) => {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  const hours = Math.floor(seconds / SECONDS_PER_HOUR);
  const minutes = Math.floor((seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE);
  const restSeconds = seconds % SECONDS_PER_MINUTE;

  if (hours > 0) return `${hours}:${pad(minutes)}:${pad(restSeconds)}`;

  return `${pad(minutes)}:${pad(restSeconds)}`;
};
