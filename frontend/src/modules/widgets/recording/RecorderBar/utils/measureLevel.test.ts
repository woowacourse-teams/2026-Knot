import { describe, expect, it } from "vitest";

import { measureLevel } from "./measureLevel";

describe("measureLevel", () => {
  it("소리가 없으면 0이다", () => {
    expect(measureLevel(new Float32Array(8))).toBe(0);
  });

  it("소리가 클수록 큰 값을 돌려준다", () => {
    const quiet = measureLevel(new Float32Array(8).fill(0.05));
    const loud = measureLevel(new Float32Array(8).fill(0.15));

    expect(loud).toBeGreaterThan(quiet);
  });

  it("아주 큰 소리여도 1을 넘지 않는다", () => {
    expect(measureLevel(new Float32Array(8).fill(1))).toBe(1);
  });

  it("음수 진폭도 크기로 센다", () => {
    expect(measureLevel(new Float32Array(8).fill(-0.1))).toBe(
      measureLevel(new Float32Array(8).fill(0.1)),
    );
  });

  it("빈 샘플이면 0이다", () => {
    expect(measureLevel(new Float32Array(0))).toBe(0);
  });
});
