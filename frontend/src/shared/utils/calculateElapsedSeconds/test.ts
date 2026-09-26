import { describe, expect, it } from "vitest";

import { calculateElapsedSeconds } from ".";

describe("calculateElapsedSeconds", () => {
  it("멈춘 상태면 쌓아 둔 시간만 초로 바꾼다", () => {
    expect(calculateElapsedSeconds(0, null, 10_000)).toBe(0);
    expect(calculateElapsedSeconds(3000, null, 10_000)).toBe(3);
  });

  it("진행 중이면 재개 뒤 흐른 시간을 더한다", () => {
    expect(calculateElapsedSeconds(0, 10_000, 12_000)).toBe(2);
    expect(calculateElapsedSeconds(3000, 10_000, 12_500)).toBe(5);
  });

  it("1초가 안 되는 나머지는 버린다", () => {
    expect(calculateElapsedSeconds(999, null, 0)).toBe(0);
    expect(calculateElapsedSeconds(500, 10_000, 11_700)).toBe(2);
  });

  it("now가 재개 시각보다 이르면 진행 구간을 0으로 본다", () => {
    expect(calculateElapsedSeconds(3000, 10_000, 9_000)).toBe(3);
  });
});
