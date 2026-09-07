/**
 * 로그인 토큰을 받아 보관하고 꺼내 주는 곳.
 *
 * 로그인 상태는 더 이상 쿠키가 아니라 `Authorization: Bearer <JWT>` 헤더로 증명해요.
 * 그래서 토큰을 둘 자리를 클라이언트가 직접 고릅니다.
 *
 * - 웹: `localStorage`. 새로고침·탭·재시작을 넘겨 로그인이 유지되던 쿠키 시절 동작을 그대로 맞춰요.
 * - 데스크톱 앱: 셸이 `window.knotDesktop.auth`로 열어 주는 저장소. 값은 main 프로세스가
 *   OS 키체인 키로 암호화해 파일에 넣습니다. 데스크톱에서 `localStorage`는 평문 파일이라 쓰지 않아요.
 *
 * 온보딩 토큰(가입을 마치기 전 10분짜리 1회용)은 액세스 토큰과 자리를 나눠 `sessionStorage`에 둡니다.
 * 같은 자리에 담으면 `/api/v1/auth/me`가 주는 401이 아직 써야 할 온보딩 토큰까지 지워 버려요.
 *
 * @see docs/electron-desktop-app-tech-plan.md 5.1 1단계 웹·데스크톱 공통 Bearer JWT
 */

/** 웹이 액세스 토큰을 두는 자리 (로드맵 Q15) */
const ACCESS_TOKEN_STORAGE_KEY = "knot.accessToken";

/** 온보딩 토큰을 두는 자리 (로드맵 Q19) */
const ONBOARDING_TOKEN_STORAGE_KEY = "knot.onboardingToken";

/** 로그인을 마친 백엔드가 리다이렉트 주소의 `#` 뒤에 실어 보내는 이름들 (기획서 5.1) */
const ACCESS_TOKEN_HASH_KEY = "access_token";
const ONBOARDING_TOKEN_HASH_KEY = "onboarding_token";

/**
 * 브라우저 저장소는 시크릿 모드나 차단 설정에서 읽기만 해도 던져요.
 * 토큰을 못 읽는 것은 로그인하지 않은 것과 같으므로 없는 값으로 다룹니다.
 */
const readStorage = (storage: Storage, key: string) => {
  try {
    return storage.getItem(key);
  } catch {
    return null;
  }
};

const writeStorage = (storage: Storage, key: string, value: string) => {
  try {
    storage.setItem(key, value);
  } catch {
    // 저장에 실패하면 이번 실행 동안만 로그인이 유지돼요. 화면을 멈출 이유는 아닙니다
  }
};

const removeStorage = (storage: Storage, key: string) => {
  try {
    storage.removeItem(key);
  } catch {
    // 지우지 못해도 로그아웃 뒤처리는 계속돼야 해요
  }
};

/** 데스크톱 셸에만 있는 저장소. 웹 브라우저에서는 `undefined`예요 */
const getDesktopAuth = () => window.knotDesktop?.auth;

/** 지금 로그인에 쓸 액세스 토큰. 없으면 null */
export const getAccessToken = async () => {
  const desktopAuth = getDesktopAuth();
  if (desktopAuth) return await desktopAuth.getToken();

  return readStorage(window.localStorage, ACCESS_TOKEN_STORAGE_KEY);
};

/** 로그인·가입 완료로 받은 액세스 토큰을 보관합니다 */
export const setAccessToken = async (token: string) => {
  const desktopAuth = getDesktopAuth();
  if (desktopAuth) {
    await desktopAuth.setToken(token);
    return;
  }

  writeStorage(window.localStorage, ACCESS_TOKEN_STORAGE_KEY, token);
};

/**
 * 액세스 토큰을 버립니다. 자격증명이 클라이언트에만 있으므로 이게 실제 로그아웃이에요.
 * 401을 받았을 때도 같은 일을 합니다. 더 쓸 수 없는 토큰이라서요.
 */
export const clearAccessToken = async () => {
  const desktopAuth = getDesktopAuth();
  if (desktopAuth) {
    await desktopAuth.clearToken();
    return;
  }

  removeStorage(window.localStorage, ACCESS_TOKEN_STORAGE_KEY);
};

/** 가입을 마칠 때 쓰는 온보딩 토큰. 없으면 null */
export const getOnboardingToken = () =>
  readStorage(window.sessionStorage, ONBOARDING_TOKEN_STORAGE_KEY);

export const clearOnboardingToken = () =>
  removeStorage(window.sessionStorage, ONBOARDING_TOKEN_STORAGE_KEY);

/**
 * 로그인 리다이렉트로 도착한 주소의 `#` 뒤에서 토큰을 꺼내 보관하고 주소창을 지웁니다.
 *
 * `#` 뒤는 서버로 전송되지 않아 액세스 로그나 `Referer`에 남지 않지만 방문 기록에는 남아요.
 * 그래서 읽자마자 `history.replaceState`로 지웁니다.
 *
 * 앱을 그리기 전에 한 번 불러야 첫 요청부터 `Authorization` 헤더가 붙어요.
 */
export const receiveLoginTokens = async () => {
  const hash = window.location.hash;
  if (!hash.startsWith("#")) return;

  const tokens = new URLSearchParams(hash.slice(1));
  const accessToken = tokens.get(ACCESS_TOKEN_HASH_KEY);
  const onboardingToken = tokens.get(ONBOARDING_TOKEN_HASH_KEY);
  if (accessToken === null && onboardingToken === null) return;

  if (accessToken !== null) await setAccessToken(accessToken);
  if (onboardingToken !== null) {
    writeStorage(
      window.sessionStorage,
      ONBOARDING_TOKEN_STORAGE_KEY,
      onboardingToken,
    );
  }

  window.history.replaceState(
    null,
    "",
    window.location.pathname + window.location.search,
  );
};
