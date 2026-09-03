import { http, HttpResponse } from "msw";

import { AUTH_CSRF_API_PATH } from "@api/fetch/api/v1/auth/csrf";
import { csrfTokenResponse } from "@api/mock/responses/auth";

export const authCsrfHandlers = [
  http.get(`*${AUTH_CSRF_API_PATH}`, () =>
    HttpResponse.json(csrfTokenResponse),
  ),
];
