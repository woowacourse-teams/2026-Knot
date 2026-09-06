/**
 * preload가 renderer(웹 SPA)에 노출하는 API 계약.
 *
 * 기획서 4.4의 인터페이스 정본이며, 이 파일 하나만 웹 SPA와 공유한다.
 * SPA는 이 타입을 `frontend/src/shared/types/desktop.ts`로 **복사**해 쓴다
 * (패키지 간 import는 두 프로젝트의 lockfile을 얽히게 하므로 하지 않는다).
 * 따라서 이 파일은 다른 파일을 import 하지 않는 자립 파일로 유지한다.
 *
 * 규칙(기획서 4.4 · 불변 계약 5):
 * - preload는 여기 정의된 것만 노출한다. `ipcRenderer` 원본은 노출하지 않는다.
 * - API 추가는 이 파일의 변경으로만 한다. 셸 업데이트가 웹 배포보다 느리므로
 *   SPA는 `window.knotDesktop?.xxx` 옵셔널 접근으로 하위 호환을 지킨다.
 */

/** `knot://` 딥링크를 파싱한 결과. 라우팅은 SPA가 한다 */
export type KnotDeepLink =
  | { type: "invite"; token: string }
  | { type: "chat"; workspaceId: string; sessionId?: string };

export interface KnotDesktopApi {
  /** 앱 버전(semver). 빌드 시점에 `package.json`에서 고정된다 */
  readonly version: string;
  readonly platform: "darwin" | "win32" | "linux";
  /** 빌드 시점에 고정되는 환경. 런타임 전환 수단은 두지 않는다(기획서 4.5) */
  readonly env: "prod" | "dev" | "local";
  /** `https:`·`mailto:`만 허용한다. 그 외 스킴은 reject */
  openExternal(url: string): Promise<void>;
  /** 구독 해제 함수를 돌려준다 */
  onDeepLink(handler: (link: KnotDeepLink) => void): () => void;
  /** 앱이 꺼져 있을 때 들어온 딥링크를 한 번 가져간다. 없으면 null */
  getPendingDeepLink(): Promise<KnotDeepLink | null>;
  // 2단계 인증(A6·A7)에서 채운다
  auth?: {
    startLogin(): Promise<void>;
    logout(): Promise<void>;
    onSessionChanged(handler: (state: "signed-in" | "signed-out") => void): () => void;
  };
  notifications?: {
    show(input: { title: string; body: string; link?: KnotDeepLink }): Promise<void>;
  };
}

/** renderer에 노출되는 전역 이름 */
export const KNOT_DESKTOP_GLOBAL = "knotDesktop";

/**
 * main ↔ preload IPC 채널 이름.
 *
 * SPA는 이 상수를 쓰지 않는다(preload 뒤에 숨는다). 복사본에서는 지워도 된다.
 */
export const IPC_CHANNELS = {
  /** invoke: (url: string) => void */
  openExternal: "knot:open-external",
  /** invoke: () => KnotDeepLink | null */
  getPendingDeepLink: "knot:get-pending-deep-link",
  /** main → renderer: KnotDeepLink */
  deepLink: "knot:deep-link",
} as const;
