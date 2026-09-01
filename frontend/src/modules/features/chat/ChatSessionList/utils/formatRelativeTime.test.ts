import { describe, it, expect } from "vitest";

import { formatRelativeTime } from "./formatRelativeTime";

const now = new Date("2026-09-01T12:00:00Z");

describe("formatRelativeTime", () => {
  it("1분이 지나지 않았으면 방금으로 읽는다", () => {
    expect(
      formatRelativeTime({ date: "2026-09-01T11:59:30Z", now }),
    ).toBe("방금");
  });

  it("한 시간 안이면 분으로 읽는다", () => {
    expect(formatRelativeTime({ date: "2026-09-01T11:20:00Z", now })).toBe(
      "40분 전",
    );
  });

  it("하루 안이면 시간으로 읽는다", () => {
    expect(formatRelativeTime({ date: "2026-09-01T04:00:00Z", now })).toBe(
      "8시간 전",
    );
  });

  it("일주일 안이면 일로 읽는다", () => {
    expect(formatRelativeTime({ date: "2026-08-30T12:00:00Z", now })).toBe(
      "2일 전",
    );
  });

  it("한 달 안이면 주로 읽는다", () => {
    expect(formatRelativeTime({ date: "2026-08-18T12:00:00Z", now })).toBe(
      "2주 전",
    );
  });

  it("한 해 안이면 달로 읽는다", () => {
    expect(formatRelativeTime({ date: "2026-06-01T12:00:00Z", now })).toBe(
      "3개월 전",
    );
  });

  it("한 해가 넘으면 해로 읽는다", () => {
    expect(formatRelativeTime({ date: "2024-09-01T12:00:00Z", now })).toBe(
      "2년 전",
    );
  });

  it("아직 오지 않은 시각도 방금으로 읽는다", () => {
    expect(formatRelativeTime({ date: "2026-09-01T12:00:10Z", now })).toBe(
      "방금",
    );
  });
});
