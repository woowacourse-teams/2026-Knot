import { describe, expect, it } from "vitest";

import { formatDateTime } from "./formatDateTime";

describe("formatDateTime", () => {
  it("사용자 시간대의 연월일과 시분을 보여 준다", () => {
    // 시간대와 무관하게 같은 결과가 나오도록 그 지역 시각으로 만들어요
    const iso = new Date(2026, 8, 9, 0, 41).toISOString();

    expect(formatDateTime(iso)).toBe("2026.09.09 00:41");
  });

  it("올바른 시각이 아니면 빈 문자열을 돌려준다", () => {
    expect(formatDateTime("언제였더라")).toBe("");
  });
});
