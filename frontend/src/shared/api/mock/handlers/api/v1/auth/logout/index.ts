import { http, HttpResponse } from "msw";

import { AUTH_LOGOUT_API_PATH } from "@api/fetch/api/v1/auth/logout";

// baseURL이 환경변수라 오리진은 와일드카드로 둬요
export const authLogoutHandlers = [
  http.post(
    `*${AUTH_LOGOUT_API_PATH}`,
    () => new HttpResponse(null, { status: 204 }),
  ),
];
