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

/**
 * 1단계 로그인(패턴 C)이 지나가는 오리진. 기획서 4.3·4.5
 *
 * `accounts.google.com`은 GitHub 계정을 Google로 만든 사용자가 `github.com/login`에서
 * "Sign in with Google"을 누를 때 지나간다(2026-09-07 실측, 로드맵 U20). 이 홉을
 * 외부 브라우저로 넘기면 Google 인증만 다른 브라우저에서 끝나고, GitHub이 소셜 로그인
 * `state`를 심어둔 세션 쿠키는 앱 세션에 남아 콜백 검증이 실패한다. 로그인 체인은
 * 한 브라우저 세션 안에서 끝나야 한다.
 *
 * Apple(`appleid.apple.com`)은 같은 이유로 아직 깨져 있다(기획서 4.5 미해소).
 */
const GITHUB_LOGIN_ORIGINS = ["https://github.com", "https://accounts.google.com"] as const;

/**
 * Notion OAuth 302 체인이 지나가는 오리진.
 *
 * `app.notion.com`은 동의 화면이다(2026-09-08 실측, 로드맵 U2). SPA가 이동하는
 * `api.notion.com/v1/oauth/authorize`가 302로 `app.notion.com/install-integration`에
 * 보내는데, 이 오리진이 없으면 `will-redirect`에서 차단돼 외부 브라우저로 빠지고
 * 앱 창의 연결 버튼은 이동 대기 상태에 갇혀 무한 로딩이 된다(기획서 4.5, R21).
 * `www.notion.so`는 아직 관측되지 않았지만 로그인 홉 가능성으로 둔다. 동의 이후
 * 홉은 미측정이라 여기 없는 도메인이 나오면 로그(`did-redirect-navigation`)에 남고
 * 외부 브라우저로 빠진다.
 */
const NOTION_OAUTH_ORIGINS = [
  "https://api.notion.com",
  "https://app.notion.com",
  "https://www.notion.so",
] as const;

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
  // 2026-09-07 정정. mock 구동(`API_MOCKING=true`)만 전제해 웹과 같은 오리진이었으나,
  // 그때는 devServer 302 미들웨어가 로그인을 대신해 백엔드로 나가는 홉이 아예 없다.
  // 실 백엔드로 로그인을 검증하려면 SPA가 `:8080/oauth2/authorization/github`로
  // 이동하므로 이 오리진이 허용 목록에 있어야 한다(기획서 4.5).
  local: "http://localhost:8080",
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
