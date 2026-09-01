import { http, HttpResponse } from "msw";

import { AUTH_NICKNAME_API_PATH } from "@api/fetch/api/v1/auth/nickname";

// 성공 시 응답 본문이 없는 엔드포인트예요
export const authNicknameHandlers = [
  http.post(
    `*${AUTH_NICKNAME_API_PATH}`,
    () => new HttpResponse(null, { status: 200 }),
  ),
];
