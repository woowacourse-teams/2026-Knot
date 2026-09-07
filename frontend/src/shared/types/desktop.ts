/**
 * 데스크톱 앱(Electron 셸)이 preload로 넣어 주는 API의 모양.
 *
 * 셸은 `https://knoted.kr`을 그대로 원격 로드하므로, 같은 번들이 브라우저에서도
 * 데스크톱에서도 돌아요. 그래서 이 값은 **항상 있다고 볼 수 없고** 쓰는 쪽은
 * `window.knotDesktop?.openExternal(...)`처럼 옵셔널로 접근해야 합니다.
 * 셸 업데이트는 웹 배포보다 느리므로 나중에 생긴 기능(`auth`·`notifications`)도
 * 옵셔널이에요.
 *
 * 정본은 셸 쪽 `desktop/src/shared/api.ts`이고 이 파일은 그 계약을 옮겨 적은 사본이에요.
 * 두 프로젝트의 lockfile을 얽히게 하지 않으려고 패키지를 넘나드는 import 대신 복사를 씁니다.
 * 계약이 바뀌면 기획서 4.4 → 셸 → 이 파일 순으로 함께 고쳐요.
 *
 * @see docs/electron-desktop-app-tech-plan.md 4.4 preload API 계약
 */

/** 딥링크(`knot://`)로 들어온 목적지 */
export type KnotDeepLink =
  | { type: "invite"; token: string }
  | { type: "chat"; workspaceId: string; sessionId?: string };

/** 데스크톱 셸이 `window.knotDesktop`으로 노출하는 API */
export interface KnotDesktopApi {
  /** 앱 버전(semver) */
  readonly version: string;
  /** 앱이 도는 OS */
  readonly platform: "darwin" | "win32" | "linux";
  /** 셸이 빌드될 때 고정된 환경. 런타임에 바뀌지 않아요 */
  readonly env: "prod" | "dev" | "local";
  /** 링크를 기본 브라우저로 엽니다. `https:`·`mailto:`만 열리고 나머지는 거절돼요 */
  openExternal(url: string): Promise<void>;
  /** 딥링크 수신을 구독합니다. 돌려주는 함수를 부르면 구독이 끊겨요 */
  onDeepLink(handler: (link: KnotDeepLink) => void): () => void;
  /** 앱이 꺼져 있을 때 눌린 딥링크. 없으면 null */
  getPendingDeepLink(): Promise<KnotDeepLink | null>;
  /**
   * 액세스 토큰 저장소. 이 저장소를 가진 셸에서는 `localStorage` 대신 여기에 토큰을 둬요.
   * 값은 main 프로세스가 OS 키체인 키로 암호화해 파일에 넣습니다.
   *
   * 셸 업데이트가 웹 배포보다 느려서 아직 이 객체가 없는 셸도 있어요.
   */
  auth?: {
    /** 저장해 둔 액세스 토큰. 없으면 null */
    getToken(): Promise<string | null>;
    /** 액세스 토큰을 암호화해 저장합니다 */
    setToken(token: string): Promise<void>;
    /** 저장한 액세스 토큰을 지웁니다. 로그아웃·401에서 불러요 */
    clearToken(): Promise<void>;
    /** 시스템 브라우저 로그인(2단계)이 붙은 셸에만 있어요 */
    startLogin?(): Promise<void>;
    /** 서버 세션 폐기까지 하는 로그아웃(2단계) */
    logout?(): Promise<void>;
    /** 셸이 토큰을 갱신하거나 잃었을 때 알려줘요(2단계) */
    onSessionChanged?(
      handler: (state: "signed-in" | "signed-out") => void,
    ): () => void;
  };
  /** 알림 기능이 붙은 셸에만 있어요 */
  notifications?: {
    show(input: {
      title: string;
      body: string;
      link?: KnotDeepLink;
    }): Promise<void>;
  };
}

declare global {
  interface Window {
    /** 데스크톱 셸에서만 있어요. 브라우저에서는 `undefined` */
    knotDesktop?: KnotDesktopApi;
  }
}
