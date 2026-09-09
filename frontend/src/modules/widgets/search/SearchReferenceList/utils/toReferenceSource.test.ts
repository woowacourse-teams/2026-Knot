import { describe, expect, it } from "vitest";

import { toReferenceSource } from "./toReferenceSource";

describe("toReferenceSource", () => {
  it("NOTION은 notion으로 바꾼다", () => {
    expect(toReferenceSource("NOTION")).toBe("notion");
  });

  it("모르는 제공자는 undefined를 돌려준다", () => {
    expect(toReferenceSource("SLACK")).toBeUndefined();
    expect(toReferenceSource("")).toBeUndefined();
  });
});
