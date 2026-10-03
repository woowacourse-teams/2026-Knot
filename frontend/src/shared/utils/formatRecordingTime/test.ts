import { describe, expect, it } from "vitest";

import { formatRecordingTime } from ".";

describe("formatRecordingTime", () => {
  it("한 시간 전까지는 분과 초를 두 자리씩 보여준다", () => {
    expect(formatRecordingTime(0)).toBe("00:00");
    expect(formatRecordingTime(5)).toBe("00:05");
    expect(formatRecordingTime(768)).toBe("12:48");
    expect(formatRecordingTime(3599)).toBe("59:59");
  });

  it("한 시간부터는 시간을 앞에 붙인다", () => {
    expect(formatRecordingTime(3600)).toBe("1:00:01");
    expect(formatRecordingTime(3723)).toBe("1:02:03");
  });

  it("소수점과 음수는 버리고 0으로 본다", () => {
    expect(formatRecordingTime(12.9)).toBe("00:12");
    expect(formatRecordingTime(-3)).toBe("00:00");
  });
});
