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

/** 데스크톱 로그인 뷰가 열려 있는지와, 뷰가 덮지 않는 상단 띠의 높이(px) */
export interface LoginPromptState {
  open: boolean;
  headerHeight: number;
}

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
    /**
     * 2단계 로그인이 붙은 셸에만 있어요. 셸은 **메인 창 안에 로그인 뷰를 붙이고** 창을
     * 새로 띄우지 않아요(재개정 2026-09-10, 기획서 5.2·로드맵 Q68).
     */
    startLogin?(): Promise<void>;
    /** 로그인 뷰 헤더 띠의 "취소". 진행 중인 로그인이 없으면 아무 일도 하지 않아요 */
    cancelLogin?(): Promise<void>;
    /** 서버 세션 폐기까지 하는 로그아웃(2단계) */
    logout?(): Promise<void>;
    /** 셸이 토큰을 갱신하거나 잃었을 때 알려줘요(2단계) */
    onSessionChanged?(
      handler: (state: "signed-in" | "signed-out") => void,
    ): () => void;
    /**
     * 로그인 뷰가 붙고 떨어질 때 알려줘요(2026-09-10).
     *
     * 뷰는 창 안쪽에서 위쪽 `headerHeight`(px)만 남기고 화면을 덮어요. 열려 있는 동안
     * 그 띠에 제목과 "취소"를 그리는 것이 웹의 몫입니다(`DesktopLoginPrompt`).
     */
    onLoginPromptChanged?(
      handler: (prompt: LoginPromptState) => void,
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
  /**
   * 앱 안 구독 탐색(기획서 6.5, 로드맵 트랙 L). 사용자 본인의 Claude 구독으로 앱이 답을 만들어요.
   *
   * 구독 토큰은 셸 main에만 있고 여기로는 상태만 내려와요. 로그인은 시스템 브라우저의 claude.ai OAuth이며
   * 끝나면 resolve, 실패·취소·타임아웃은 reject돼요. 이 객체가 없는 셸·브라우저는 서버 SSE 경로만 써요.
   */
  llm?: {
    /** 구독 로그인 상태. 거절되지 않고 항상 값을 돌려줘요 */
    getStatus(): Promise<LlmSubscriptionStatus>;
    /** claude.ai OAuth(시스템 브라우저)로 구독에 로그인해요 */
    signIn(): Promise<void>;
    /** 셸에 저장된 구독 자격증명을 지워요. claude.ai 세션 자체는 그대로예요 */
    signOut(): Promise<void>;
    /** 로그인·갱신·로그아웃·호출 결과로 상태가 바뀔 때. 돌려주는 함수를 부르면 구독이 끊겨요 */
    onStatusChanged(
      handler: (status: LlmSubscriptionStatus) => void,
    ): () => void;
    /**
     * 질문을 사용자 구독으로 스트리밍해요. 셸이 서버 검색 근거로 프롬프트를 만들어 호출하고 답변·출처를
     * 서버에 저장한 뒤 `complete`를 줘요. 이벤트 모양은 서버 SSE 경로와 같아요.
     * 돌려주는 함수를 부르면 취소되고, 그 뒤로는 어떤 콜백도 오지 않아요.
     */
    streamAnswer(input: LlmStreamInput, on: LlmStreamHandlers): () => void;
    /** 설정 화면용. 현재 모델·effort와 셸이 허용하는 목록 */
    getSettings(): Promise<LlmSettingsView>;
    /** 목록 밖 값이면 거절돼요. 바뀌면 `onStatusChanged`(model)도 와요 */
    updateSettings(input: {
      model: string;
      effort: string;
    }): Promise<LlmSettingsView>;
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

/** `llm.streamAnswer` 입력. ID는 라우트 값이라 문자열이에요 */
export interface LlmStreamInput {
  workspaceId: string;
  sessionId: string;
  content: string;
}

/**
 * `llm.streamAnswer`의 실패. `fallback`이 true면 첫 조각 전의 구독 쪽 실패(미로그인·401·크레딧 소진 등)라
 * 같은 질문을 서버 SSE 경로로 다시 보내면 돼요. false면 서버 SSE로 보내도 같은 결과예요
 */
export interface LlmStreamError {
  code: string;
  message: string;
  fallback: boolean;
}

/** `llm.streamAnswer` 콜백. 서버 SSE 경로의 chunk·complete·error와 같은 모양이에요 */
export interface LlmStreamHandlers {
  chunk(delta: string): void;
  complete(res: { messageId: number }): void;
  error(err: LlmStreamError): void;
}

/** 구독 호출 설정. 고를 수 있는 값은 셸이 줘요 — 화면이 모델 목록을 하드코딩하지 않아요 */
export interface LlmSettingsView {
  /** 현재 모델 ID */
  model: string;
  /** 현재 effort */
  effort: string;
  /** 선택 가능한 모델 ID */
  models: readonly string[];
  /** 선택 가능한 effort 값 */
  efforts: readonly string[];
}

/** 사용자 Claude 구독 로그인 상태. 토큰 값은 절대 담기지 않아요 */
export interface LlmSubscriptionStatus {
  /** 사용자 Claude 구독에 로그인돼 있는가 */
  signedIn: boolean;
  /** access 토큰 만료(ISO 8601). 셸이 만료 전에 자동 갱신해요. 로그인 전이면 null */
  expiresAt: string | null;
  /** 현재 모델. 기본 `claude-fable-5-1` */
  model: string;
  /** 마지막 호출·갱신 오류 코드. 없으면 null */
  lastError: string | null;
  /** 마지막 질문이 어느 경로로 응답됐는지. 구독이면 `subscription`, 서버 폴백이면 `server-sse` */
  lastAnsweredBy: "subscription" | "server-sse" | null;
}

declare global {
  interface Window {
    /** 데스크톱 셸에서만 있어요. 브라우저에서는 `undefined` */
    knotDesktop?: KnotDesktopApi;
  }
}
