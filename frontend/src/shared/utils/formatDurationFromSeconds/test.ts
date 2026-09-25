import { describe, expect, it } from "vitest";

import { formatDurationFromSeconds } from ".";

describe("formatDurationFromSeconds", () => {
  it("0초는 0분으로 보여준다", () => {
    expect(formatDurationFromSeconds(0)).toBe("0분");
  });

  it("1초부터 59초까지는 0분으로 보이지 않도록 1분으로 보여준다", () => {
    expect(formatDurationFromSeconds(1)).toBe("1분");
    expect(formatDurationFromSeconds(59)).toBe("1분");
  });

  it("1분부터는 남은 초를 내림한다", () => {
    expect(formatDurationFromSeconds(60)).toBe("1분");
    expect(formatDurationFromSeconds(119)).toBe("1분");
    expect(formatDurationFromSeconds(120)).toBe("2분");
  });

  it("25분 0초부터 25분 59초까지는 25분으로 보여준다", () => {
    expect(formatDurationFromSeconds(1500)).toBe("25분");
    expect(formatDurationFromSeconds(1559)).toBe("25분");
    expect(formatDurationFromSeconds(1560)).toBe("26분");
  });

  it("59분 59초까지는 분으로만 보여준다", () => {
    expect(formatDurationFromSeconds(3599)).toBe("59분");
  });

  it("60분부터 60분 59초까지는 1시간으로 보여준다", () => {
    expect(formatDurationFromSeconds(3600)).toBe("1시간");
    expect(formatDurationFromSeconds(3659)).toBe("1시간");
    expect(formatDurationFromSeconds(3660)).toBe("1시간 1분");
  });

  it("1시간 12분 0초부터 1시간 12분 59초까지는 1시간 12분으로 보여준다", () => {
    expect(formatDurationFromSeconds(4320)).toBe("1시간 12분");
    expect(formatDurationFromSeconds(4379)).toBe("1시간 12분");
    expect(formatDurationFromSeconds(4380)).toBe("1시간 13분");
  });

  it("정각이면 남은 초가 있어도 분을 붙이지 않는다", () => {
    expect(formatDurationFromSeconds(7200)).toBe("2시간");
    expect(formatDurationFromSeconds(7259)).toBe("2시간");
  });

  it("값이 이상하면 0분으로 보여준다", () => {
    expect(formatDurationFromSeconds(-1)).toBe("0분");
    expect(formatDurationFromSeconds(Number.NaN)).toBe("0분");
    expect(formatDurationFromSeconds(Number.POSITIVE_INFINITY)).toBe("0분");
  });
});
