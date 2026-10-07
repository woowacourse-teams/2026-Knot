import { createHttpError, HTTP_ERROR_TYPE } from "@api/httpClient/error";
import { AxiosError } from "axios";
import { describe, expect, it } from "vitest";

import { isRetryableAudioUploadError } from ".";

describe("isRetryableAudioUploadError", () => {
  it("응답이 없는 네트워크 오류면 true를 돌려준다", () => {
    expect(
      isRetryableAudioUploadError(
        createHttpError({ type: HTTP_ERROR_TYPE.network }),
      ),
    ).toBe(true);
  });

  it("5xx면 true를 돌려준다", () => {
    expect(
      isRetryableAudioUploadError(
        createHttpError({ type: HTTP_ERROR_TYPE.serverError }),
      ),
    ).toBe(true);
  });

  it("저장소에 아직 파일이 없다는 오류 코드면 true를 돌려준다", () => {
    expect(
      isRetryableAudioUploadError(
        createHttpError({
          type: HTTP_ERROR_TYPE.conflict,
          code: "AUDIO_UPLOAD_NOT_COMPLETED",
        }),
      ),
    ).toBe(true);
  });

  it("저장소 PUT이 실패하면 true를 돌려준다", () => {
    expect(isRetryableAudioUploadError(new AxiosError("PUT 실패"))).toBe(true);
  });

  it("그 밖의 4xx면 false를 돌려준다", () => {
    expect(
      isRetryableAudioUploadError(
        createHttpError({ type: HTTP_ERROR_TYPE.badRequest }),
      ),
    ).toBe(false);
    expect(
      isRetryableAudioUploadError(
        createHttpError({
          type: HTTP_ERROR_TYPE.conflict,
          code: "RECORDING_ALREADY_DISCARDED",
        }),
      ),
    ).toBe(false);
  });

  it("요청 실패가 아니면 false를 돌려준다", () => {
    expect(isRetryableAudioUploadError(new Error("boom"))).toBe(false);
    expect(isRetryableAudioUploadError(undefined)).toBe(false);
  });
});
