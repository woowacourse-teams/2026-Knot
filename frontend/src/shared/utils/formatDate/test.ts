import { describe, expect, it } from "vitest";

import { formatDate } from ".";

// 실행 환경의 시간대와 무관하게 같은 날짜가 되도록 로컬 시각으로 ISO 문자열을 만들어요
const toLocalIso = (year: number, monthIndex: number, day: number, hour = 12) =>
  new Date(year, monthIndex, day, hour).toISOString();

describe("formatDate", () => {
  it("ISO 시각을 '연 월 일' 문구로 보여준다", () => {
    expect(formatDate(toLocalIso(2026, 8, 15))).toBe("2026년 9월 15일");
  });

  it("한 자리 월·일은 앞에 0을 붙이지 않는다", () => {
    expect(formatDate(toLocalIso(2026, 0, 5))).toBe("2026년 1월 5일");
  });

  it("사용자 시간대 기준 날짜로 보여준다", () => {
    expect(formatDate(toLocalIso(2026, 11, 31, 23))).toBe("2026년 12월 31일");
    expect(formatDate(toLocalIso(2027, 0, 1, 0))).toBe("2027년 1월 1일");
  });
});
