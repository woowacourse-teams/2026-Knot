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
 * - 개정 전 `chat`·`llm` API(2026-09-07·08판, `S3`)는 폐기됐다(로드맵 `S8`에서 제거). 지금의 `llm`은
 *   트랙 L(앱 안 구독 탐색, 기획서 6.5)의 것으로 자격증명 출처가 다르다(사용자 Claude 구독 OAuth).
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

/**
 * 사용자 Claude 구독 로그인 상태(기획서 6.5, 로드맵 `L1`). 구독 access·refresh 토큰 값은 절대 담지 않는다.
 */
export interface LlmSubscriptionStatus {
  /** 사용자 Claude 구독 로그인 여부 */
  signedIn: boolean;
  /** access 토큰 만료(ISO 8601). 만료 전 main이 자동 갱신한다. 로그인돼 있지 않으면 null */
  expiresAt: string | null;
  /** 현재 모델(로드맵 Q62 기본 `claude-fable-5-1`) */
  model: string;
  /** 마지막 호출·갱신 오류 코드(`invalid_grant`·401·크레딧 소진 등). 없으면 null */
  lastError: string | null;
  /** 마지막 질문이 어느 경로로 응답됐는지(구독=subscription, 폴백=server-sse). 아직 없으면 null */
  lastAnsweredBy: "subscription" | "server-sse" | null;
}

/**
 * 구독 호출 설정(기획서 6.5, 로드맵 Q62·Q67). 선택 가능한 값은 main이 준다 — SPA가 모델 목록을 하드코딩하지 않는다.
 */
export interface LlmSettingsView {
  model: string;
  effort: string;
  /** 선택 가능한 모델 ID */
  models: readonly string[];
  /** `output_config.effort` 값 */
  efforts: readonly string[];
}

/** `llm.streamAnswer` 입력. ID는 라우트 값이라 문자열이다(`workspaceId`는 로드맵 Q65) */
export interface LlmStreamInput {
  workspaceId: string;
  sessionId: string;
  content: string;
}

/** `llm.streamAnswer`의 `error` 콜백 값. `fallback`이 true면 renderer가 서버 SSE 경로로 같은 질문을 다시 보낸다(Q66) */
export interface LlmStreamError {
  code: string;
  message: string;
  fallback: boolean;
}

export interface LlmStreamHandlers {
  chunk(delta: string): void;
  complete(res: { messageId: number }): void;
  error(err: LlmStreamError): void;
}

/**
 * main → renderer 스트림 이벤트(IPC `knot:llm-stream-event`). preload가 `requestId`로 자기 스트림만 골라 콜백에 넘긴다.
 * 웹 채팅이 받는 모양(`chunk {delta}`·`complete {messageId}`·`error {code, message}`)과 같다(불변 계약 1번).
 */
export type LlmStreamEventPayload =
  | { requestId: string; event: "chunk"; delta: string }
  | { requestId: string; event: "complete"; messageId: number }
  | { requestId: string; event: "error"; code: string; message: string; fallback: boolean };

/**
 * 로그인 뷰의 열림 상태(A7, 2026-09-10, 기획서 5.2).
 *
 * `open`이면 메인 창 안에 로그인 뷰가 붙어 있고, 위쪽 `headerHeight`(px)만 SPA가 보인다.
 */
export interface LoginPromptState {
  open: boolean;
  /** 뷰가 덮지 않는 상단 띠의 높이(px). 닫혀 있으면 0 */
  headerHeight: number;
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
    // 2단계 인증(A6·A7). 이 셸부터 채워져 있다(로드맵 Q60)
    /**
     * 메인 창 안 로그인 뷰를 붙여 로그인한다(재개정 2026-09-10, 로드맵 Q68 — 창을 새로 만들지 않는다).
     * 끝나면 resolve, 실패·취소·타임아웃은 reject.
     */
    startLogin?(): Promise<void>;
    /** 로그인 뷰 헤더의 "취소". 대기 중인 로그인이 없으면 아무 일도 하지 않는다(2026-09-10) */
    cancelLogin?(): Promise<void>;
    /** 기기 세션 폐기 API 호출(실패해도 계속) + 로컬 토큰 삭제 */
    logout?(): Promise<void>;
    /** 셸이 세션을 얻거나 잃었을 때. 구독 해제 함수를 돌려준다 */
    onSessionChanged?(handler: (state: "signed-in" | "signed-out") => void): () => void;
    /**
     * 로그인 뷰가 붙고 떨어질 때(2026-09-10, 기획서 5.2).
     *
     * 뷰는 메인 창 content 영역에서 위쪽 `headerHeight`만 남기고 덮는다. SPA는 열려 있는 동안
     * 그 띠에 제목과 "취소"를 그린다 — 그리지 않으면 빈 띠만 남고 취소는 뷰 안 `Esc`뿐이다(R31).
     */
    onLoginPromptChanged?(handler: (prompt: LoginPromptState) => void): () => void;
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
  /**
   * 앱 안 구독 탐색(기획서 6.5, 로드맵 트랙 L). 데스크톱 셸에서 채팅 화면·설정 화면(`L3`)이 쓴다.
   *
   * 구독 토큰은 main의 `safeStorage`(`subscription-auth.bin`)에만 있고 어떤 응답·이벤트에도 실리지 않는다.
   * 로그인은 시스템 브라우저의 claude.ai OAuth이며 renderer에는 성공(resolve)·실패(reject 메시지)만 온다.
   */
  llm?: {
    getStatus(): Promise<LlmSubscriptionStatus>;
    /** claude.ai OAuth(시스템 브라우저). 끝나면 resolve, 실패·취소·타임아웃은 reject */
    signIn(): Promise<void>;
    /** `subscription-auth.bin` 삭제. Anthropic 세션 자체는 폐기하지 않는다 */
    signOut(): Promise<void>;
    /** 로그인·갱신·로그아웃·호출 결과로 상태가 바뀔 때. 구독 해제 함수를 돌려준다 */
    onStatusChanged(handler: (status: LlmSubscriptionStatus) => void): () => void;
    /**
     * 질문을 사용자 구독으로 스트리밍한다(`L2`). main이 서버 검색 근거로 `system`을 조립해 호출하고 답변·출처를 서버에
     * 저장한 뒤 `complete`를 준다. 반환값은 취소 함수 — 취소하면 이후 이벤트가 오지 않는다.
     */
    streamAnswer(input: LlmStreamInput, on: LlmStreamHandlers): () => void;
    /** 설정 화면(`L3`)용. 현재 모델·effort와 허용 목록 */
    getSettings(): Promise<LlmSettingsView>;
    /** 목록 밖 값은 reject. 바뀌면 `onStatusChanged`(model)도 온다 */
    updateSettings(input: { model: string; effort: string }): Promise<LlmSettingsView>;
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
  /** invoke: () => void — 메인 창 안 로그인 뷰의 로그인이 끝나면 resolve, 실패·취소는 reject(A7) */
  authStartLogin: "knot:auth-start-login",
  /** invoke: () => void — 로그인 뷰 헤더의 "취소"(A7, 2026-09-10) */
  authCancelLogin: "knot:auth-cancel-login",
  /** invoke: () => void — 기기 세션 폐기 + 로컬 토큰 삭제(A7) */
  authLogout: "knot:auth-logout",
  /** main → renderer: "signed-in" | "signed-out" (A7) */
  authSessionChanged: "knot:auth-session-changed",
  /** main → renderer: LoginPromptState — 로그인 뷰가 붙고 떨어질 때(A7, 2026-09-10) */
  authLoginPrompt: "knot:auth-login-prompt",
  /** invoke: ({title, body, link?}) => void (A10) */
  notificationsShow: "knot:notifications-show",
  /** invoke: () => AgentBridgeStatus */
  agentStatus: "knot:agent-status",
  /** invoke: (target: AgentRegistrationTarget) => { preview: string } */
  agentCopyRegistration: "knot:agent-copy-registration",
  /** invoke: () => void */
  agentRotateToken: "knot:agent-rotate-token",
  /** invoke: (port: number) => void */
  agentSetPort: "knot:agent-set-port",
  /** invoke: () => LlmSubscriptionStatus (L1) */
  llmStatus: "knot:llm-status",
  /** invoke: () => void — claude.ai OAuth가 끝나면 resolve, 실패·취소는 reject(L1) */
  llmSignIn: "knot:llm-sign-in",
  /** invoke: () => void — 구독 자격증명 삭제(L1) */
  llmSignOut: "knot:llm-sign-out",
  /** main → renderer: LlmSubscriptionStatus (L1) */
  llmStatusChanged: "knot:llm-status-changed",
  /** invoke: ({requestId, workspaceId, sessionId, content}) => void — 입력 검사 뒤 스트림을 시작하고 바로 resolve(L2) */
  llmStream: "knot:llm-stream",
  /** main → 호출한 renderer: LlmStreamEventPayload (L2) */
  llmStreamEvent: "knot:llm-stream-event",
  /** invoke: ({requestId}) => void — 진행 중인 스트림 취소(L2) */
  llmStreamCancel: "knot:llm-stream-cancel",
  /** invoke: () => LlmSettingsView (L3) */
  llmSettings: "knot:llm-settings",
  /** invoke: ({model, effort}) => LlmSettingsView — main이 허용 목록으로 재검사(L3) */
  llmUpdateSettings: "knot:llm-update-settings",
} as const;
