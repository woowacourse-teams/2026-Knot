import type { CreateAxiosDefaults } from "axios";
import axios from "axios";

import type { HttpInterceptor } from "../types/interceptor";

/**
 * 인터셉터를 `use`로 이어 붙이고 `build`로 axios 인스턴스를 만드는 빌더를 만듭니다.
 *
 * 먼저 `use`한 인터셉터가 바깥을 감싸요. 그래서 요청은 먼저 넣은 인터셉터부터,
 * 응답은 나중에 넣은 인터셉터부터 거쳐 갑니다.
 * `use`는 기존 빌더를 바꾸지 않고 새 빌더를 돌려줘요.
 *
 * @example
 * const client = createHttpClientBuilder({ baseURL: "/api" })
 *   .use(csrfInterceptor)
 *   .build();
 */
export const createHttpClientBuilder = (
  config: CreateAxiosDefaults = {},
  interceptors: HttpInterceptor[] = [],
) => ({
  use: (interceptor: HttpInterceptor) =>
    createHttpClientBuilder(config, [...interceptors, interceptor]),

  build: () => {
    const client = axios.create(config);
    const adapter = axios.getAdapter(config.adapter ?? axios.defaults.adapter);

    client.defaults.adapter = interceptors.reduceRight(
      (next, interceptor) => interceptor(next, client),
      adapter,
    );

    return client;
  },
});
