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
 * - 개정 전 `chat`·`llm` API(2026-09-07·08판, `S3`)는 폐기됐다(로드맵 `S8`에서 제거).
 */

/** `knot://` 딥링크를 파싱한 결과. 라우팅은 SPA가 한다 */
export type KnotDeepLink =
  | { type: "invite"; token: string }
  | { type: "chat"; workspaceId: string; sessionId?: string };

/** CLI 에이전트 등록 스니펫의 대상(기획서 6.4, 로드맵 Q50). `skill`은 스킬 설치 명령이다 */
export type AgentRegistrationTarget = "claude-code" | "codex" | "gemini" | "skill";

/** 로컬 MCP 서버(CLI 에이전트 연결) 상태. 연결 토큰 값은 절대 담지 않는다 */
export interface AgentBridgeStatus {
  running: boolean;
  port: number;
  /** `http://127.0.0.1:<port>/mcp` */
  url: string;
  /** 포트 충돌 등 기동 실패 사유. 정상이면 null */
  error: string | null;
  /** 연결 토큰 발급 시각(ISO 8601) */
  tokenIssuedAt: string;
  /** 마지막 도구 호출 시각(연결 확인용). 없으면 null */
  lastToolCallAt: string | null;
  /** 앱이 복사해 둔 SKILL.md 절대 경로(복사 명령용) */
  skillPath: string;
}

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
  /**
   * 액세스 토큰 저장소(기획서 5.1). 값은 main이 `safeStorage`로 암호화해 파일에 둔다.
   *
   * SPA는 이 저장소만 쓰고 헤더는 스스로 붙인다. 2단계(A7)에서 main이
   * `onBeforeSendHeaders`로 주입하게 되면 `getToken`이 null을 돌려주도록 바꾼다.
   */
  auth?: {
    /** 저장된 액세스 토큰. 없으면 null */
    getToken(): Promise<string | null>;
    /** 액세스 토큰을 암호화해 저장한다 */
    setToken(token: string): Promise<void>;
    /** 저장된 액세스 토큰을 지운다(로그아웃·401) */
    clearToken(): Promise<void>;
    // 2단계 인증(A6·A7)에서 채운다
    startLogin?(): Promise<void>;
    logout?(): Promise<void>;
    onSessionChanged?(handler: (state: "signed-in" | "signed-out") => void): () => void;
  };
  /**
   * CLI 에이전트 연결(기획서 6.4, 로드맵 `S8`). 데스크톱 전용 연결 안내 화면(`S9`)이 쓴다.
   *
   * 연결 토큰은 어떤 응답에도 실리지 않는다. `copyRegistration`은 main이 토큰이 든 스니펫을
   * 클립보드에 쓰고, renderer에는 토큰을 가린 미리보기만 돌려준다.
   */
  agent?: {
    getStatus(): Promise<AgentBridgeStatus>;
    copyRegistration(target: AgentRegistrationTarget): Promise<{ preview: string }>;
    /** 연결 토큰 재발급. 기존 CLI 등록은 무효가 된다 */
    rotateToken(): Promise<void>;
    /** 1024~65535. MCP 서버를 다시 연다(앱 재시작 없음) */
    setPort(port: number): Promise<void>;
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
  /** invoke: () => string | null */
  authGetToken: "knot:auth-get-token",
  /** invoke: (token: string) => void */
  authSetToken: "knot:auth-set-token",
  /** invoke: () => void */
  authClearToken: "knot:auth-clear-token",
  /** invoke: () => AgentBridgeStatus */
  agentStatus: "knot:agent-status",
  /** invoke: (target: AgentRegistrationTarget) => { preview: string } */
  agentCopyRegistration: "knot:agent-copy-registration",
  /** invoke: () => void */
  agentRotateToken: "knot:agent-rotate-token",
  /** invoke: (port: number) => void */
  agentSetPort: "knot:agent-set-port",
} as const;
