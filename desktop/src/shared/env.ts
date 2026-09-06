/**
 * 오리진·환경 정책 (기획서 4.5의 표를 코드로 옮긴 것).
 *
 * 여기의 값이 네비게이션 허용 목록과 IPC sender 검증의 근거다.
 * 값을 바꾸면 기획서 4.5를 먼저 고친다(문서 우선 원칙).
 */

import type { KnotDesktopApi } from "./api";

export type KnotEnvName = KnotDesktopApi["env"];

export interface KnotEnvironment {
  readonly name: KnotEnvName;
  /** `BrowserWindow`가 로드하는 웹 오리진. IPC sender 검증 기준이기도 하다 */
  readonly webOrigin: string;
  /** SPA가 호출하는 API 오리진. 셸이 직접 호출하지는 않고 허용 목록에만 쓴다 */
  readonly apiOrigin: string;
  /** 앱 창에서 네비게이션을 허용할 오리진 전체 */
  readonly navigationAllowlist: readonly string[];
}

/** 1단계 로그인(패턴 C)이 지나가는 오리진. 기획서 4.3 */
const GITHUB_LOGIN_ORIGINS = ["https://github.com"] as const;

/**
 * Notion OAuth 302 체인이 지나가는 오리진.
 *
 * 로드맵 U2: 실제 체인은 A1 스파이크에서 실측해 확정한다. 여기 없는 도메인이
 * 나오면 로그(`did-redirect-navigation`)에 남고 외부 브라우저로 빠진다.
 */
const NOTION_OAUTH_ORIGINS = ["https://api.notion.com", "https://www.notion.so"] as const;

const WEB_ORIGINS: Readonly<Record<KnotEnvName, string>> = {
  prod: "https://knoted.kr",
  dev: "https://dev.knoted.kr",
  local: "http://localhost:3000",
};

/**
 * 저장소에 값이 있는 API 오리진.
 *
 * prod는 `vars.API_BASE_URL_PROD`가 저장소에 없으므로 null이며, 빌드 환경변수
 * `KNOT_API_ORIGIN`으로 주입해야 한다(로드맵 Q3 기본값).
 */
const KNOWN_API_ORIGINS: Readonly<Record<KnotEnvName, string | null>> = {
  prod: null,
  // 2026-09-06 A1 실측값. `frontend/.env.local`의 `api.dev.knoted.kr`이 아니라
  // 배포된 dev SPA가 실제로 이동하는 오리진이다(지식 §1.1 정정).
  // `api.<env>.knoted.kr` 대칭 가정은 깨졌으므로 prod 값을 추정하지 않는다.
  dev: "https://dev-api.knoted.kr",
  local: "http://localhost:3000",
};

export function isKnotEnvName(value: string): value is KnotEnvName {
  return value === "prod" || value === "dev" || value === "local";
}

/** URL 문자열에서 오리진만 뽑는다. 파싱 실패는 null */
export function toOrigin(rawUrl: string): string | null {
  try {
    return new URL(rawUrl).origin;
  } catch {
    return null;
  }
}

/**
 * 빌드 시점 상수로 환경을 확정한다.
 *
 * @param name 빌드 대상 환경
 * @param injectedApiOrigin prod 빌드에서 주입된 API 오리진(로드맵 Q3). dev·local은 null 가능
 */
export function resolveEnvironment(
  name: KnotEnvName,
  injectedApiOrigin: string | null = null,
): KnotEnvironment {
  const webOrigin = WEB_ORIGINS[name];
  const apiOrigin = injectedApiOrigin ?? KNOWN_API_ORIGINS[name];

  if (apiOrigin === null) {
    throw new Error(
      `${name} 빌드에는 API 오리진이 필요하다. 빌드 환경변수 KNOT_API_ORIGIN으로 주입한다(로드맵 Q3).`,
    );
  }

  const normalizedApiOrigin = toOrigin(apiOrigin);
  if (normalizedApiOrigin === null) {
    throw new Error(`API 오리진이 URL이 아니다: ${apiOrigin}`);
  }

  const navigationAllowlist = [
    ...new Set([
      webOrigin,
      normalizedApiOrigin,
      ...GITHUB_LOGIN_ORIGINS,
      ...NOTION_OAUTH_ORIGINS,
    ]),
  ];

  return {
    name,
    webOrigin,
    apiOrigin: normalizedApiOrigin,
    navigationAllowlist,
  };
}
