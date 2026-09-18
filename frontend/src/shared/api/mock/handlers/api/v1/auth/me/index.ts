import { http, HttpResponse } from "msw";

import { AUTH_ME_API_PATH } from "@api/fetch/api/v1/auth/me";
import { meResponse } from "@api/mock/responses/auth";

// baseURL이 환경변수라 오리진은 와일드카드로 둬요
export const authMeHandlers = [
  http.get(`*${AUTH_ME_API_PATH}`, () => HttpResponse.json(meResponse)),
];
