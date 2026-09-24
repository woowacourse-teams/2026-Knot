import { describe, expect, it } from "vitest";

import { formatDurationFromSeconds } from ".";

describe("formatDurationFromSeconds", () => {
  it("1분이 안 되는 녹음도 올려서 1분으로 보여준다", () => {
    expect(formatDurationFromSeconds(1)).toBe("1분");
    expect(formatDurationFromSeconds(30)).toBe("1분");
    expect(formatDurationFromSeconds(59)).toBe("1분");
  });

  it("초가 조금이라도 남으면 다음 분으로 올린다", () => {
    expect(formatDurationFromSeconds(60)).toBe("1분");
    expect(formatDurationFromSeconds(61)).toBe("2분");
    expect(formatDurationFromSeconds(1501)).toBe("26분");
  });

  it("59분까지는 분으로만 보여준다", () => {
    expect(formatDurationFromSeconds(3540)).toBe("59분");
  });

  it("올려서 60분이 되면 1시간으로 보여준다", () => {
    expect(formatDurationFromSeconds(3541)).toBe("1시간");
    expect(formatDurationFromSeconds(3600)).toBe("1시간");
  });

  it("한 시간이 넘으면 시간과 분을 함께 보여준다", () => {
    expect(formatDurationFromSeconds(4320)).toBe("1시간 12분");
    expect(formatDurationFromSeconds(4321)).toBe("1시간 13분");
  });

  it("정각이면 분을 붙이지 않는다", () => {
    expect(formatDurationFromSeconds(7200)).toBe("2시간");
  });

  it("녹음이 없거나 값이 이상하면 0분으로 보여준다", () => {
    expect(formatDurationFromSeconds(0)).toBe("0분");
    expect(formatDurationFromSeconds(-1)).toBe("0분");
    expect(formatDurationFromSeconds(Number.NaN)).toBe("0분");
    expect(formatDurationFromSeconds(Number.POSITIVE_INFINITY)).toBe("0분");
  });
});
