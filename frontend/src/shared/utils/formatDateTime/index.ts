const padTwoDigits = (value: number) => String(value).padStart(2, "0");

/**
 * ISO 8601 시각을 화면용 `YYYY.MM.DD HH:mm`(사용자 시간대)으로 바꿉니다.
 * 올바른 시각이 아니면 빈 문자열을 돌려줘요.
 *
 * @param iso - ISO 8601 문자열
 * @returns 화면용 시각 문구
 * @example
 * formatDateTime("2026-09-09T00:41:00+09:00"); // "2026.09.09 00:41" (KST 기준)
 */
export const formatDateTime = (iso: string) => {
  const date = new Date(iso);

  if (Number.isNaN(date.getTime())) return "";

  const day = [
    date.getFullYear(),
    padTwoDigits(date.getMonth() + 1),
    padTwoDigits(date.getDate()),
  ].join(".");
  const time = [date.getHours(), date.getMinutes()].map(padTwoDigits).join(":");

  return `${day} ${time}`;
};
