import { describe, expect, it } from "vitest";

import { formatReferenceLocation } from "./formatReferenceLocation";

describe("formatReferenceLocation", () => {
  it("제공자 이름과 마지막 수정일을 함께 보여 준다", () => {
    // 시간대와 무관하게 같은 날짜가 나오도록 그 지역의 정오로 만들어요
    const updatedAt = new Date(2026, 8, 1, 12).toISOString();

    expect(formatReferenceLocation({ source: "NOTION", updatedAt })).toBe(
      "Notion · 2026.09.01 수정",
    );
  });

  it("모르는 제공자는 이름을 그대로 쓴다", () => {
    const updatedAt = new Date(2026, 0, 5, 12).toISOString();

    expect(formatReferenceLocation({ source: "SLACK", updatedAt })).toBe(
      "SLACK · 2026.01.05 수정",
    );
  });

  it("수정 시각이 올바르지 않으면 제공자만 보여 준다", () => {
    expect(
      formatReferenceLocation({ source: "NOTION", updatedAt: "언제였더라" }),
    ).toBe("Notion");
  });
});
