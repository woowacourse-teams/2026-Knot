interface FormatRelativeTimeParams {
  date: string;
  now?: Date;
}

const MINUTE = 60 * 1000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
const WEEK = 7 * DAY;
const MONTH = 30 * DAY;
const YEAR = 365 * DAY;

/**
 * 대화가 마지막으로 오간 시각을 "2일 전"처럼 지금 기준의 문구로 만듭니다.
 *
 * 목록에서는 정확한 시각보다 얼마나 지났는지가 중요해 가장 큰 단위 하나만 씁니다.
 *
 * @param date - 기준이 되는 시각(ISO 문자열)
 * @param now - 비교 기준 시각. 기본값은 현재 시각
 * @returns 지금 기준의 경과 문구. 1분이 지나지 않았으면 `"방금"`
 *
 * @example
 * formatRelativeTime({ date: "2026-08-30T12:00:00Z", now: new Date("2026-09-01T12:00:00Z") });
 * // "2일 전"
 */
export const formatRelativeTime = ({
  date,
  now = new Date(),
}: FormatRelativeTimeParams) => {
  const elapsed = now.getTime() - new Date(date).getTime();

  if (elapsed < MINUTE) return "방금"; // 시계 오차로 미래 시각이 와도 방금으로 읽습니다
  if (elapsed < HOUR) return `${Math.floor(elapsed / MINUTE)}분 전`;
  if (elapsed < DAY) return `${Math.floor(elapsed / HOUR)}시간 전`;
  if (elapsed < WEEK) return `${Math.floor(elapsed / DAY)}일 전`;
  if (elapsed < MONTH) return `${Math.floor(elapsed / WEEK)}주 전`;
  if (elapsed < YEAR) return `${Math.floor(elapsed / MONTH)}개월 전`;

  return `${Math.floor(elapsed / YEAR)}년 전`;
};
