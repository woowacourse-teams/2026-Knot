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
   * 탐색 데스크톱 경유(기획서 6.4). 이벤트 모양은 웹 SSE의 `ChatStreamEvent`와 같다.
   *
   * main이 서버 검색 API → 사용자 LLM → 서버 저장 API를 차례로 부르고, delta마다
   * `chunk`, 끝나면 `complete {messageId}`, 실패하면 `error {code, message}`를 준다.
   * `cancel()` 뒤에는 이벤트가 오지 않는다. `complete`·`error` 뒤에도 더 오지 않는다.
   */
  chat?: {
    ask(
      input: { sessionId: number; content: string },
      onEvent: (event: ChatStreamEvent) => void,
    ): Promise<{ cancel(): void }>;
  };
  /**
   * 사용자 LLM 설정(기획서 6.4). 키 값은 돌려주지 않는다(`hasApiKey`만).
   *
   * 저장된 설정이 없으면 `getSettings`는 provider `openai-compatible`에 `baseUrl`·`model`이
   * 빈 문자열인 값을 돌려준다(로드맵 Q44). `setSettings`의 `apiKey`는 값이 있을 때만 바꾸고
   * 비어 있으면 기존 키를 유지한다. 키를 지우는 것은 `clearApiKey`뿐이다.
   */
  llm?: {
    getSettings(): Promise<UserLlmSettings>;
    setSettings(input: UserLlmSettingsInput): Promise<void>;
    clearApiKey(): Promise<void>;
  };
  notifications?: {
    show(input: { title: string; body: string; link?: KnotDeepLink }): Promise<void>;
  };
}

/** 탐색 스트림 이벤트. 웹 SSE 계약(`chunk`·`complete`·`error`)과 같은 모양이다 */
export type ChatStreamEvent =
  | { event: "chunk"; data: { delta: string } }
  | { event: "complete"; data: { messageId: number } }
  | { event: "error"; data: { code: string; message: string } };

/** 사용자 LLM 종류. `openai-compatible`은 LM Studio·Ollama·OpenAI 호환 서비스, `anthropic`은 Messages API */
export type UserLlmProvider = "openai-compatible" | "anthropic";

export interface UserLlmSettings {
  provider: UserLlmProvider;
  /** 예: `http://localhost:1234/v1`, `https://api.anthropic.com`. 허용 범위는 로드맵 Q27 */
  baseUrl: string;
  model: string;
  /** 키가 저장돼 있는가. 값은 절대 renderer로 나가지 않는다 */
  hasApiKey: boolean;
}

export type UserLlmSettingsInput = Omit<UserLlmSettings, "hasApiKey"> & { apiKey?: string };

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
  /** invoke: (input: { sessionId: number; content: string }) => requestId */
  chatAsk: "knot:chat-ask",
  /** invoke: (requestId: string) => void */
  chatCancel: "knot:chat-cancel",
  /** main → renderer: { requestId: string; event: ChatStreamEvent } */
  chatEvent: "knot:chat-event",
  /** invoke: () => UserLlmSettings */
  llmGetSettings: "knot:llm-get-settings",
  /** invoke: (input: UserLlmSettingsInput) => void */
  llmSetSettings: "knot:llm-set-settings",
  /** invoke: () => void */
  llmClearApiKey: "knot:llm-clear-api-key",
} as const;
