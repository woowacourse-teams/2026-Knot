import type { AxiosAdapter, AxiosInstance } from "axios";
import axios from "axios";
import { describe, expect, it } from "vitest";

import type { HttpInterceptor } from "../types/interceptor";
import { createHttpClientBuilder } from ".";

/** 네트워크 없이 성공 응답을 돌려주는 어댑터 */
const okAdapter: AxiosAdapter = async (config) => ({
  data: "응답",
  status: 200,
  statusText: "OK",
  headers: {},
  config,
});

/** 실행 순서를 `calls`에 남기는 인터셉터 */
const tracing =
  (name: string, calls: string[]): HttpInterceptor =>
  (next) =>
  async (config) => {
    calls.push(`${name}:요청`);
    const response = await next(config);
    calls.push(`${name}:응답`);

    return response;
  };

describe("createHttpClientBuilder", () => {
  it("요청은 먼저 use한 인터셉터부터, 응답은 나중에 use한 인터셉터부터 거쳐 간다", async () => {
    const calls: string[] = [];
    const client = createHttpClientBuilder({ adapter: okAdapter })
      .use(tracing("first", calls))
      .use(tracing("second", calls))
      .build();

    await client.get("/");

    expect(calls).toEqual([
      "first:요청",
      "second:요청",
      "second:응답",
      "first:응답",
    ]);
  });

  it("인터셉터 없이 빌드하면 설정의 어댑터를 그대로 쓴다", async () => {
    const client = createHttpClientBuilder({ adapter: okAdapter }).build();

    const { data } = await client.get("/");

    expect(data).toBe("응답");
  });

  it("인터셉터는 next를 다시 불러 요청을 다시 보낼 수 있다", async () => {
    let attempts = 0;
    const flakyAdapter: AxiosAdapter = async (config) => {
      attempts += 1;
      if (attempts === 1) throw new axios.AxiosError("실패", "ERR_TEST", config);

      return okAdapter(config);
    };
    const retryOnce: HttpInterceptor = (next) => (config) =>
      next(config).catch(() => next(config));

    const client = createHttpClientBuilder({ adapter: flakyAdapter })
      .use(retryOnce)
      .build();

    const { data } = await client.get("/");

    expect(data).toBe("응답");
    expect(attempts).toBe(2);
  });

  it("인터셉터는 빌드된 인스턴스를 받는다", async () => {
    let received: AxiosInstance | undefined;
    const capture: HttpInterceptor = (next, client) => {
      received = client;
      return next;
    };

    const client = createHttpClientBuilder({ adapter: okAdapter })
      .use(capture)
      .build();

    expect(received).toBe(client);
  });

  it("use는 기존 빌더를 바꾸지 않고 새 빌더를 돌려준다", async () => {
    const calls: string[] = [];
    const base = createHttpClientBuilder({ adapter: okAdapter });
    base.use(tracing("added", calls));

    await base.build().get("/");

    expect(calls).toEqual([]);
  });
});
