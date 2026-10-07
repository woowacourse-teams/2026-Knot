import { afterEach, describe, expect, it, vi } from "vitest";

import { logRequestError } from ".";

describe("logRequestError", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("동작 이름과 오류를 콘솔 에러로 남긴다", () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    const error = new Error("boom");

    logRequestError("일시정지", error);

    expect(consoleError).toHaveBeenCalledWith("일시정지 요청에 실패했어요", error);
  });
});
