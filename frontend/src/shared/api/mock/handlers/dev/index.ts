import { http, HttpResponse } from "msw";

import { AUTH_ME_API_PATH } from "@api/fetch/api/v1/auth/me";
import { AUTH_NICKNAME_API_PATH } from "@api/fetch/api/v1/auth/nickname";
import { meResponse } from "@api/mock/responses/auth";
import type { NotionOAuthAuthorizationResponse } from "@api/mock/types/notionConnection";

/**
 * 개발 서버의 mock 로그인 상태 쿠키.
 *
 * `webpack.config.js`의 mock OAuth 미들웨어가 심고 여기 핸들러가 읽어요.
 * 이름이나 값을 바꾸면 그 미들웨어·`playwright.config.ts`의 storageState와 함께 바꿔야 해요.
 */
export const MOCK_AUTH_COOKIE_NAME = "KNOT_MOCK_AUTH";

/** 실제 백엔드의 접근 토큰(member)·온보딩 토큰(onboarding) 구분을 쿠키 값으로 흉내내요 */
export const MOCK_AUTH_STATUS = {
  MEMBER: "member",
  ONBOARDING: "onboarding",
} as const;

/**
 * 개발 브라우저 전용 인증 핸들러.
 *
 * 실제 백엔드처럼 쿠키로 로그인 상태를 판정해 GitHub 로그인 → 온보딩 → 홈 진입
 * 플로우를 개발 서버에서 재현해요. OAuth 진입 자체는 페이지 네비게이션이라 msw가
 * 가로채지 못하므로 `webpack.config.js`의 devServer 미들웨어가 302로 대신해요.
 *
 * `browser.ts`에서만 기본 핸들러 앞에 둬(먼저 맞는 핸들러가 이겨요) me·nickname을
 * 덮어요. vitest(server.ts)는 항상 로그인된 기본 핸들러를 그대로 쓰므로 넣지 않아요.
 */
export const devAuthHandlers = [
  http.get(`*${AUTH_ME_API_PATH}`, ({ cookies }) => {
    if (cookies[MOCK_AUTH_COOKIE_NAME] !== MOCK_AUTH_STATUS.MEMBER) {
      return new HttpResponse(null, { status: 401 });
    }

    return HttpResponse.json(meResponse);
  }),

  http.post(`*${AUTH_NICKNAME_API_PATH}`, ({ cookies }) => {
    if (cookies[MOCK_AUTH_COOKIE_NAME] === undefined) {
      return new HttpResponse(null, { status: 401 });
    }

    // 온보딩 토큰 → 접근 토큰 전환을 재현해요. msw의 Set-Cookie 저장은 새로고침하면
    // 사라지므로 document.cookie에 직접 써요 (resolver는 페이지 컨텍스트에서 돌아요)
    document.cookie = `${MOCK_AUTH_COOKIE_NAME}=${MOCK_AUTH_STATUS.MEMBER}; path=/; SameSite=Lax`;

    return new HttpResponse(null, { status: 200 });
  }),
];

/**
 * 개발 브라우저 전용 Notion OAuth 핸들러.
 *
 * 기본 핸들러는 실제 Notion 인증 URL을 돌려주는데, client_id가 가짜라 개발 서버에서
 * 실제로 이동하면 Notion이 거절해 플로우가 거기서 끊겨요. 그래서 실제 흐름(Notion 동의
 * → `GET /api/v1/notion/oauth/callback` → 303)이 끝났을 때 도착하는 같은 오리진의 결과
 * 화면 URL을 인증 URL 자리에 바로 돌려줘, 연결 시작 → 결과 처리 → 홈 이동까지 개발
 * 서버에서 재현해요. 실패 화면은 주소창에서 `result=failed`로 바꿔 확인하세요.
 *
 * `devAuthHandlers`처럼 `browser.ts`에서만 기본 핸들러 앞에 둬요. vitest는 노션 연동
 * 카드 테스트가 실제 Notion URL로의 이동을 확인하므로 기본 핸들러를 그대로 써요.
 */
export const devNotionOAuthHandlers = [
  http.post(
    "*/api/v1/workspaces/:workspaceId/notion-oauth-authorizations",
    ({ params }) =>
      HttpResponse.json(
        {
          // 결과 파라미터는 NotionConnectCard constants/notionConnect.ts(connected|failed)와 같은 약속이에요
          authorizationUrl: `/workspace/${String(params.workspaceId)}/notion-connection?result=connected`,
        } satisfies NotionOAuthAuthorizationResponse,
        { status: 201 },
      ),
  ),
];
