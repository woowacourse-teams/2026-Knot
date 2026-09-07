import { http, HttpResponse } from "msw";

import { AUTH_NICKNAME_API_PATH } from "@api/fetch/api/v1/auth/nickname";
import { nicknameResponse } from "@api/mock/responses/auth";

export const authNicknameHandlers = [
  http.post(`*${AUTH_NICKNAME_API_PATH}`, () =>
    HttpResponse.json(nicknameResponse),
  ),
];
