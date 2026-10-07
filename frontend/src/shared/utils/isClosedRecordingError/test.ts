import { createHttpError, HTTP_ERROR_TYPE } from "@api/httpClient/error";
import { describe, expect, it } from "vitest";

import { isClosedRecordingError } from ".";

describe("isClosedRecordingError", () => {
  it("이미 버려진 녹음 오류 코드면 true를 돌려준다", () => {
    expect(
      isClosedRecordingError(
        createHttpError({ type: HTTP_ERROR_TYPE.conflict, code: "RECORDING_ALREADY_DISCARDED" }),
      ),
    ).toBe(true);
  });

  it("이미 끝난 녹음 오류 코드면 true를 돌려준다", () => {
    expect(
      isClosedRecordingError(
        createHttpError({ type: HTTP_ERROR_TYPE.conflict, code: "RECORDING_ALREADY_ENDED" }),
      ),
    ).toBe(true);
  });

  it("다른 오류 코드거나 코드가 없으면 false를 돌려준다", () => {
    expect(
      isClosedRecordingError(createHttpError({ type: HTTP_ERROR_TYPE.conflict, code: "OTHER" })),
    ).toBe(false);
    expect(isClosedRecordingError(createHttpError({ type: HTTP_ERROR_TYPE.conflict }))).toBe(false);
  });

  it("응답이 없는 네트워크 에러면 false를 돌려준다", () => {
    expect(isClosedRecordingError(createHttpError({ type: HTTP_ERROR_TYPE.network }))).toBe(false);
  });

  it("HTTP 에러가 아니면 false를 돌려준다", () => {
    expect(isClosedRecordingError(new Error("boom"))).toBe(false);
    expect(isClosedRecordingError(undefined)).toBe(false);
  });
});
