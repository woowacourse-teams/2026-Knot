import { AxiosError, type AxiosResponse } from "axios";
import { describe, expect, it } from "vitest";

import { getPreviewErrorMessage } from "./getPreviewErrorMessage";

const NOT_FOUND_MESSAGE = "올바르지 않은 코드예요. 다시 확인해 주세요.";
const TOO_MANY_REQUESTS_MESSAGE =
  "요청이 너무 많아요. 잠시 후 다시 시도해 주세요.";
const UNKNOWN_MESSAGE = "코드를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.";

const createAxiosError = (status?: number) => {
  const response =
    status === undefined ? undefined : ({ status } as AxiosResponse);

  return new AxiosError(
    "request failed",
    undefined,
    undefined,
    undefined,
    response,
  );
};

describe("getPreviewErrorMessage", () => {
  it("404면 올바르지 않은 코드 문구를 돌려준다", () => {
    expect(getPreviewErrorMessage(createAxiosError(404))).toBe(
      NOT_FOUND_MESSAGE,
    );
  });

  it("429면 요청이 너무 많다는 문구를 돌려준다", () => {
    expect(getPreviewErrorMessage(createAxiosError(429))).toBe(
      TOO_MANY_REQUESTS_MESSAGE,
    );
  });

  it("그 외 상태 코드면 확인하지 못했다는 문구를 돌려준다", () => {
    expect(getPreviewErrorMessage(createAxiosError(500))).toBe(UNKNOWN_MESSAGE);
    expect(getPreviewErrorMessage(createAxiosError(401))).toBe(UNKNOWN_MESSAGE);
  });

  it("응답이 없거나 axios 에러가 아니어도 확인하지 못했다는 문구를 돌려준다", () => {
    expect(getPreviewErrorMessage(createAxiosError())).toBe(UNKNOWN_MESSAGE);
    expect(getPreviewErrorMessage(new Error("boom"))).toBe(UNKNOWN_MESSAGE);
  });
});
