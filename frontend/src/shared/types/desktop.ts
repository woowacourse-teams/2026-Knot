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
  /**
   * CLI 에이전트 연결(기획서 6.4). 데스크톱 전용 연결 안내 화면(`/agent-connection`)이 써요.
   *
   * 셸이 띄운 로컬 MCP 서버의 상태를 읽고, 사용자의 CLI(Claude Code·Codex CLI·Gemini CLI)에
   * 등록할 명령·설정을 클립보드로 복사해요. 연결 토큰 값은 이 API로 내려오지 않고 main이
   * 클립보드에 직접 쓰므로, 화면에는 토큰을 가린 미리보기(`preview`)만 보여 줍니다.
   */
  agent?: {
    /** 로컬 MCP 서버의 현재 상태. 거절되지 않고 항상 값을 돌려줘요 */
    getStatus(): Promise<AgentBridgeStatus>;
    /**
     * 등록 스니펫(연결 토큰 포함)을 main이 클립보드에 써요. 토큰을 가린 미리보기만 돌려줍니다.
     * MCP 서버가 꺼져 있어도 동작하고, 클립보드에 쓰지 못했을 때만 거절돼요.
     */
    copyRegistration(
      target: AgentRegistrationTarget,
    ): Promise<{ preview: string }>;
    /** 연결 토큰을 다시 발급해요. 기존 CLI 등록은 무효가 되니 다시 복사해 등록해야 해요 */
    rotateToken(): Promise<void>;
    /**
     * MCP 서버 포트를 바꾸고 다시 열어요(앱 재시작 없음). 1024~65535 정수가 아니면 거절돼요.
     * 포트 충돌은 거절이 아니라 `getStatus()`의 `running: false`·`error`로 드러나요.
     * 저장·재기동을 시작하면 곧바로 응답하므로, 열렸는지는 잠시(약 1초) 뒤 `getStatus()`로 다시 읽어요.
     */
    setPort(port: number): Promise<void>;
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

/** 등록 스니펫을 복사할 대상. `skill`은 Knot 스킬(`SKILL.md`) 설치 명령이에요 */
export type AgentRegistrationTarget =
  "claude-code" | "codex" | "gemini" | "skill";

/** 로컬 MCP 서버(CLI 에이전트 연결)의 상태 */
export interface AgentBridgeStatus {
  /** MCP 서버가 열려 있는가 */
  running: boolean;
  /** 서버가 듣는 포트. 기본 47871 */
  port: number;
  /** 등록 스니펫에 들어가는 서버 주소. `http://127.0.0.1:<port>/mcp` */
  url: string;
  /** 포트 충돌 등 기동 실패 사유. 정상이면 null */
  error: string | null;
  /** 연결 토큰 발급 시각(ISO 8601) */
  tokenIssuedAt: string;
  /** 마지막 도구 호출 시각(ISO 8601). 아직 없으면 null. CLI가 실제로 붙었는지 확인하는 용도예요 */
  lastToolCallAt: string | null;
  /** 앱 리소스 안 `SKILL.md`의 절대 경로. 스킬 설치 명령이 이 파일을 복사해요 */
  skillPath: string;
}

declare global {
  interface Window {
    /** 데스크톱 셸에서만 있어요. 브라우저에서는 `undefined` */
    knotDesktop?: KnotDesktopApi;
  }
}
