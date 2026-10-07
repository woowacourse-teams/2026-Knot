import { createHttpError, HTTP_ERROR_TYPE } from "@api/httpClient/error";
import { describe, expect, it } from "vitest";

import { getConnectErrorMessage } from "./getConnectErrorMessage";

const FORBIDDEN_ERROR_MESSAGE =
  "워크스페이스 소유자만 노션을 연결할 수 있어요.";
const UNKNOWN_ERROR_MESSAGE =
  "노션 연결을 시작하지 못했어요. 잠시 후 다시 시도해 주세요.";


describe("getConnectErrorMessage", () => {
  it("403이면 소유자만 연결할 수 있다는 문구를 돌려준다", () => {
    expect(getConnectErrorMessage(createHttpError({ type: HTTP_ERROR_TYPE.forbidden }))).toBe(
      FORBIDDEN_ERROR_MESSAGE,
    );
  });

  it("그 외 상태 코드면 잠시 후 다시 시도 문구를 돌려준다", () => {
    expect(getConnectErrorMessage(createHttpError({ type: HTTP_ERROR_TYPE.serverError }))).toBe(
      UNKNOWN_ERROR_MESSAGE,
    );
    expect(getConnectErrorMessage(createHttpError({ type: HTTP_ERROR_TYPE.badRequest }))).toBe(
      UNKNOWN_ERROR_MESSAGE,
    );
  });

  it("응답이 없거나 HTTP 에러가 아니어도 잠시 후 다시 시도 문구를 돌려준다", () => {
    expect(getConnectErrorMessage(createHttpError({ type: HTTP_ERROR_TYPE.network }))).toBe(
      UNKNOWN_ERROR_MESSAGE,
    );
    expect(getConnectErrorMessage(new Error("boom"))).toBe(
      UNKNOWN_ERROR_MESSAGE,
    );
  });
});
