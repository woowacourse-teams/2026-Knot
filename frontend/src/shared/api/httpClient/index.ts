import { createHttpClientBuilder } from "./builder";
import {
  csrfInterceptor,
  getCsrfToken as getCsrfTokenWith,
} from "./interceptors/csrf";
import { httpErrorInterceptor } from "./interceptors/httpError";

const TIMEOUT_DURATION = 10_000;

export const httpClient = createHttpClientBuilder({
  baseURL: process.env.API_BASE_URL,
  timeout: TIMEOUT_DURATION,
  withCredentials: true,
})
  .use(httpErrorInterceptor)
  .use(csrfInterceptor)
  .build();

/** httpClient가 쓰는 CSRF 토큰을 돌려줍니다. 없으면 받아와요. */
export const getCsrfToken = () => getCsrfTokenWith(httpClient);
