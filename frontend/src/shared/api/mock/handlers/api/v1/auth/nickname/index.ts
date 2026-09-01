import { http, HttpResponse } from "msw";

import { AUTH_NICKNAME_API_PATH } from "@api/fetch/api/v1/auth/nickname";

export const authNicknameHandlers = [
  http.post(
    `*${AUTH_NICKNAME_API_PATH}`,
    () => new HttpResponse(null, { status: 200 }),
  ),
];
