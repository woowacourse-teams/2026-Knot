import { describe, it, expect } from "vitest";

import { formatSourceLabel } from "./formatSourceLabel";

describe("formatSourceLabel", () => {
  it("전체 문서 수와 찾은 문서 수를 문구로 만든다", () => {
    expect(formatSourceLabel({ totalCount: 112, foundCount: 3 })).toBe(
      "문서 112개에서 3개를 찾았어요",
    );
  });

  it("네 자리가 넘는 수는 천 단위로 끊어 읽기 쉽게 만든다", () => {
    expect(formatSourceLabel({ totalCount: 12345, foundCount: 1000 })).toBe(
      "문서 12,345개에서 1,000개를 찾았어요",
    );
  });

  it("찾은 문서가 없으면 근거 문구를 만들지 않는다", () => {
    expect(formatSourceLabel({ totalCount: 112, foundCount: 0 })).toBeNull();
  });
});
