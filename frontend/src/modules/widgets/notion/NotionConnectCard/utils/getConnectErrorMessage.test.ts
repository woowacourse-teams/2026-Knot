import { AxiosError, type AxiosResponse } from "axios";
import { describe, expect, it } from "vitest";

import { getConnectErrorMessage } from "./getConnectErrorMessage";

const FORBIDDEN_ERROR_MESSAGE =
  "워크스페이스 소유자만 노션을 연결할 수 있어요.";
const UNKNOWN_ERROR_MESSAGE =
  "노션 연결을 시작하지 못했어요. 잠시 후 다시 시도해 주세요.";

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

describe("getConnectErrorMessage", () => {
  it("403이면 소유자만 연결할 수 있다는 문구를 돌려준다", () => {
    expect(getConnectErrorMessage(createAxiosError(403))).toBe(
      FORBIDDEN_ERROR_MESSAGE,
    );
  });

  it("그 외 상태 코드면 잠시 후 다시 시도 문구를 돌려준다", () => {
    expect(getConnectErrorMessage(createAxiosError(500))).toBe(
      UNKNOWN_ERROR_MESSAGE,
    );
    expect(getConnectErrorMessage(createAxiosError(400))).toBe(
      UNKNOWN_ERROR_MESSAGE,
    );
  });

  it("응답이 없거나 axios 에러가 아니어도 잠시 후 다시 시도 문구를 돌려준다", () => {
    expect(getConnectErrorMessage(createAxiosError())).toBe(
      UNKNOWN_ERROR_MESSAGE,
    );
    expect(getConnectErrorMessage(new Error("boom"))).toBe(
      UNKNOWN_ERROR_MESSAGE,
    );
  });
});
