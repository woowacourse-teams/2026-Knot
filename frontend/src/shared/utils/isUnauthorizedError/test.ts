import { AxiosError, type AxiosResponse } from "axios";
import { describe, expect, it } from "vitest";

import { isUnauthorizedError } from ".";

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

describe("isUnauthorizedError", () => {
  it("401 응답의 axios 에러면 true를 돌려준다", () => {
    expect(isUnauthorizedError(createAxiosError(401))).toBe(true);
  });

  it("401이 아닌 상태 코드면 false를 돌려준다", () => {
    expect(isUnauthorizedError(createAxiosError(403))).toBe(false);
    expect(isUnauthorizedError(createAxiosError(404))).toBe(false);
  });

  it("응답이 없는 네트워크 에러면 false를 돌려준다", () => {
    expect(isUnauthorizedError(createAxiosError())).toBe(false);
  });

  it("axios 에러가 아니면 false를 돌려준다", () => {
    expect(isUnauthorizedError(new Error("boom"))).toBe(false);
    expect(isUnauthorizedError(undefined)).toBe(false);
  });
});
