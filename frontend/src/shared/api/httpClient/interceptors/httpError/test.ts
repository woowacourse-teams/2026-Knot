import type { AxiosAdapter } from "axios";
import axios from "axios";
import { describe, expect, it } from "vitest";

import { createHttpClientBuilder } from "../../builder";
import { HTTP_ERROR_TYPE, isHttpError } from "../../error";
import { httpErrorInterceptor } from ".";

/** `status` 응답으로 실패하는 어댑터. 본문은 `data`로 줘요 */
const failingAdapter =
  (status: number, data?: unknown): AxiosAdapter =>
  async (config) => {
    throw new axios.AxiosError("실패", "ERR_BAD_RESPONSE", config, null, {
      data,
      status,
      statusText: "",
      headers: {},
      config,
    });
  };

const requestWith = (adapter: AxiosAdapter) =>
  createHttpClientBuilder({ adapter })
    .use(httpErrorInterceptor)
    .build()
    .get("/");

const catchError = (request: Promise<unknown>) =>
  request.then(
    () => undefined,
    (error: unknown) => error,
  );

describe("httpErrorInterceptor", () => {
  it.each([
    [400, HTTP_ERROR_TYPE.badRequest],
    [401, HTTP_ERROR_TYPE.unauthorized],
    [403, HTTP_ERROR_TYPE.forbidden],
    [404, HTTP_ERROR_TYPE.notFound],
    [409, HTTP_ERROR_TYPE.conflict],
    [429, HTTP_ERROR_TYPE.tooManyRequests],
    [422, HTTP_ERROR_TYPE.clientError],
    [500, HTTP_ERROR_TYPE.serverError],
    [503, HTTP_ERROR_TYPE.serverError],
  ])("%i 응답은 %s 오류로 바꾼다", async (status, type) => {
    const error = await catchError(requestWith(failingAdapter(status)));

    expect(isHttpError(error, type)).toBe(true);
  });

  it("응답이 없는 실패는 network 오류로 바꾼다", async () => {
    const adapter: AxiosAdapter = async (config) => {
      throw new axios.AxiosError("Network Error", "ERR_NETWORK", config);
    };

    const error = await catchError(requestWith(adapter));

    expect(isHttpError(error, HTTP_ERROR_TYPE.network)).toBe(true);
  });

  it("응답 본문의 서버 오류 코드를 담는다", async () => {
    const error = await catchError(
      requestWith(failingAdapter(409, { code: "RECORDING_ALREADY_ENDED" })),
    );

    expect(isHttpError(error) && error.code).toBe("RECORDING_ALREADY_ENDED");
  });

  it("어댑터가 문자열로 준 JSON 본문에서도 서버 오류 코드를 담는다", async () => {
    const error = await catchError(
      requestWith(
        failingAdapter(
          409,
          JSON.stringify({ code: "RECORDING_ALREADY_ENDED" }),
        ),
      ),
    );

    expect(isHttpError(error) && error.code).toBe("RECORDING_ALREADY_ENDED");
  });

  it("JSON이 아닌 문자열 본문이면 서버 오류 코드를 비워 둔다", async () => {
    const error = await catchError(
      requestWith(failingAdapter(502, "<html>Bad Gateway</html>")),
    );

    expect(isHttpError(error) && error.code).toBeUndefined();
  });

  it("4xx 오류만 클라이언트 오류로 표시한다", async () => {
    const clientError = await catchError(requestWith(failingAdapter(404)));
    const serverError = await catchError(requestWith(failingAdapter(500)));

    expect(isHttpError(clientError) && clientError.isClientError).toBe(true);
    expect(isHttpError(serverError) && serverError.isClientError).toBe(false);
  });

  it("취소된 요청은 바꾸지 않고 그대로 던진다", async () => {
    const adapter: AxiosAdapter = async () => {
      throw new axios.CanceledError();
    };

    const error = await catchError(requestWith(adapter));

    expect(axios.isCancel(error)).toBe(true);
  });

  it("다른 type으로 물으면 false다", async () => {
    const error = await catchError(requestWith(failingAdapter(404)));

    expect(isHttpError(error, HTTP_ERROR_TYPE.forbidden)).toBe(false);
  });
});
