import type { AxiosAdapter, AxiosInstance } from "axios";

/**
 * httpClient에 거는 인터셉터 하나. 관심사(CSRF, 인증 만료 등) 하나당 하나씩 만들어요.
 *
 * 다음 단계(`next`)를 받아, 그것을 감싼 새 단계를 돌려주는 함수입니다.
 * `next(config)` 앞은 요청을 보내기 전, 뒤는 응답을 받은 뒤에 실행돼요.
 * 실패 응답은 `next`가 던지는 오류로 받고, `next`를 다시 불러 요청을 다시 보낼 수 있습니다.
 * 토큰 발급처럼 따로 요청을 보내야 하면 `client`를 씁니다.
 *
 * @example
 * const withLogging: HttpInterceptor = (next) => async (config) => {
 *   console.log("요청", config.url);
 *   const response = await next(config);
 *   console.log("응답", response.status);
 *   return response;
 * };
 */
export type HttpInterceptor = (
  next: AxiosAdapter,
  client: AxiosInstance,
) => AxiosAdapter;
