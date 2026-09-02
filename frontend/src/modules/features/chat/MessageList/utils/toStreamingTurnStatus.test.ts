import { describe, expect, it } from "vitest";

import { toStreamingTurnStatus } from "./toStreamingTurnStatus";

describe("toStreamingTurnStatus", () => {
  it("아직 조각이 오지 않았으면 질문만 있는 상태다", () => {
    expect(toStreamingTurnStatus({ answer: "", isFailed: false })).toBe("pending");
  });

  it("조각이 오는 중이면 스트리밍 상태다", () => {
    expect(toStreamingTurnStatus({ answer: "부분", isFailed: false })).toBe(
      "streaming",
    );
  });

  it("끊겼으면 부분 답변이 있어도 실패 상태다", () => {
    expect(toStreamingTurnStatus({ answer: "부분", isFailed: true })).toBe("error");
  });

  it("한 글자도 못 받고 끊겨도 실패 상태다", () => {
    expect(toStreamingTurnStatus({ answer: "", isFailed: true })).toBe("error");
  });
});
