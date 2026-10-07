import { createHttpError, HTTP_ERROR_TYPE } from "@api/httpClient/error";
import { describe, expect, it } from "vitest";

import { isUnauthorizedError } from ".";


describe("isUnauthorizedError", () => {
  it("401 응답의 HTTP 에러면 true를 돌려준다", () => {
    expect(isUnauthorizedError(createHttpError({ type: HTTP_ERROR_TYPE.unauthorized }))).toBe(true);
  });

  it("401이 아닌 상태 코드면 false를 돌려준다", () => {
    expect(isUnauthorizedError(createHttpError({ type: HTTP_ERROR_TYPE.forbidden }))).toBe(false);
    expect(isUnauthorizedError(createHttpError({ type: HTTP_ERROR_TYPE.notFound }))).toBe(false);
  });

  it("응답이 없는 네트워크 에러면 false를 돌려준다", () => {
    expect(isUnauthorizedError(createHttpError({ type: HTTP_ERROR_TYPE.network }))).toBe(false);
  });

  it("HTTP 에러가 아니면 false를 돌려준다", () => {
    expect(isUnauthorizedError(new Error("boom"))).toBe(false);
    expect(isUnauthorizedError(undefined)).toBe(false);
  });
});
