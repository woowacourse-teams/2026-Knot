/**
 * ISO 시각을 `2026년 9월 15일`처럼 연·월·일 문구로 바꿔요.
 *
 * 사용자 시간대 기준 날짜를 쓰고, 한 자리 월·일 앞에 0을 붙이지 않아요.
 *
 * @example
 * formatDate("2026-09-15T01:00:00Z") // "2026년 9월 15일" (한국 시간 기준)
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2098-29280 Search/FoundRecords}
 */
export const formatDate = (isoDate: string) => {
  const date = new Date(isoDate);

  return `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일`;
};
