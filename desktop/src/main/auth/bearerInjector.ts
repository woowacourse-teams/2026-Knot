/**
 * API 오리진 요청에 `Authorization: Bearer`를 main이 주입한다 (기획서 5.2 "Bearer 주입").
 *
 * `session.webRequest.onBeforeSendHeaders`를 API 오리진(`{API}/*`)에만 건다. 다른 오리진에는
 * 절대 붙이지 않는다(불변 계약 10번). renderer가 이미 헤더를 붙였으면 건드리지 않으므로,
 * SPA가 `auth.getToken`으로 스스로 헤더를 붙이는 현행 방식과 겹쳐도 결과는 같다
 * (로드맵 Q55 — `getToken`을 null로 바꾸는 것은 SPA가 헤더를 떼는 변경과 함께 한다).
 * 페이지 이동(`mainFrame`·`subFrame`)에는 붙이지 않는다 — `/oauth2/authorization/github`
 * 같은 로그인 진입 네비게이션에 만료된 토큰이 실리면 필터가 401을 줄 수 있다(로드맵 Q41).
 */

import type { Session } from "electron";

export interface BearerRequest {
  url: string;
  /** Electron `OnBeforeSendHeadersListenerDetails.resourceType` */
  resourceType: string;
  requestHeaders: Record<string, string>;
}

const NAVIGATION_RESOURCE_TYPES = new Set(["mainFrame", "subFrame"]);

function hasAuthorization(headers: Record<string, string>): boolean {
  return Object.keys(headers).some((name) => name.toLowerCase() === "authorization");
}

function originOf(rawUrl: string): string | null {
  try {
    return new URL(rawUrl).origin;
  } catch {
    return null;
  }
}

/**
 * 주입한 헤더. 바꿀 것이 없으면 null(요청을 그대로 보낸다).
 *
 * 토큰이 없거나, 이미 `Authorization`이 있거나, API 오리진이 아니거나, 페이지 이동이면 null이다.
 */
export function withBearerHeader(
  request: BearerRequest,
  apiOrigin: string,
  token: string | null,
): Record<string, string> | null {
  if (token === null || token.length === 0) return null;
  if (NAVIGATION_RESOURCE_TYPES.has(request.resourceType)) return null;
  if (originOf(request.url) !== apiOrigin) return null;
  if (hasAuthorization(request.requestHeaders)) return null;
  return { ...request.requestHeaders, Authorization: `Bearer ${token}` };
}

/** Electron 접착. 세션당 한 리스너만 걸린다(다시 걸면 교체된다) */
export function attachBearerInjector(session: Session, apiOrigin: string, readToken: () => string | null): void {
  session.webRequest.onBeforeSendHeaders({ urls: [`${apiOrigin}/*`] }, (details, callback) => {
    const headers = withBearerHeader(
      { url: details.url, resourceType: details.resourceType, requestHeaders: details.requestHeaders },
      apiOrigin,
      readToken(),
    );
    callback(headers === null ? {} : { requestHeaders: headers });
  });
}
