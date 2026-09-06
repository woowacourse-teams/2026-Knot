# Knot 데스크톱 앱(Electron) 기술 기획서

- 문서 상태: Draft (팀 검토 전)
- 기준일: 2026-09-04 (인증 설계는 2026-09-06 개정 — 3절 `D11`, 5.1)
- 기준 커밋: `develop` `b1d4801` (`[BE] 채팅 답변 출처 조회 API 구현 (#351)`)
- 선행 문서: [`llm-electron-subscription-architecture-review.md`](./llm-electron-subscription-architecture-review.md) (이하 "검토 문서")
- 근거 자료: [`electron-desktop-app-knowledge.md`](./electron-desktop-app-knowledge.md) (이하 "지식 문서"). 본문에서 `지식 §n`으로 참조한다.
- 실행 정본: [`electron-desktop-app-roadmap.md`](./electron-desktop-app-roadmap.md) — 작업 목록·상태·게이트·미결 결정은 이 기획서가 아니라 로드맵이 정본이다.
- 관련 ADR: [`232`](./adr/232-shared-subscription-token-local-ai-review.md), [`254`](./adr/254-notion-public-oauth-connection-policy.md), [`271`](./adr/271-llm-search-architecture-benchmark.md), [`314`](./adr/314-auth-entry-redirect-branching.md)

이 문서는 기획 초안이며 ADR이 아니다. 여기 적힌 결정은 Knot 공통 Issue 계약(`docs/harness/issue-planning.md`)에 따라 Issue 기획 → 인터뷰 → Grill → 구현 브랜치의 `Proposed` ADR로 확정한다(14절).

## 1. 배경과 결론

검토 문서는 "Electron + 사용자 Claude 구독 OAuth + 백엔드 검색 전용" 제안을 세 층위(정책 차단, 대규모 신규 개발, 백엔드 계약 역전)에서 적용 불가로 판정했다. 본 기획은 그 판정을 전제로 받아들이고, **정책과 기존 계약을 지키면서 Electron 데스크톱 앱을 만드는 방법**을 정의한다.

한 줄 결론:

> Knot 데스크톱 앱은 `https://knoted.kr` 웹 앱을 원격 로드하는 Electron 셸이다. 채팅·검색·LLM 호출·답변 저장은 기존 Spring 백엔드 계약을 그대로 쓴다. 데스크톱이 더하는 것은 상시 실행, 딥링크, 알림, 퀵 질문 창, 자동 업데이트다. 인증은 웹·데스크톱 모두 `Authorization: Bearer <JWT>`를 쓰고 쿠키를 쓰지 않는다. 토큰은 웹이 `localStorage`, 데스크톱이 main의 `safeStorage`에 둔다(`D11`, 5.1). 2단계에서 시스템 브라우저 로그인 + 리프레시 토큰(디바이스 세션)을 얹는다. 채팅 모델을 Claude로 바꾸는 일은 Electron과 무관하게 백엔드 `LlmClient`에 Anthropic 어댑터를 추가해 해결한다.

제안 대비 무엇이 달라졌는지:

| 제안(검토 대상) | 본 기획 | 이유 |
| --- | --- | --- |
| Electron Main이 Agent SDK로 사용자 구독으로 모델 호출 | 백엔드가 Console API 키로 Anthropic Messages API 호출(검토 문서 7절 B안) | 서드파티 제품의 claude.ai 로그인·구독 한도 제공은 사전 승인 없이 금지(지식 §6.1) |
| in-process MCP `search_knowledge` 툴 + `POST /v1/search` | 백엔드가 LLM 호출 전 검색 결과를 system prompt에 주입(현행). 재검색은 서버 측 tool use 플래그로 선택 | 검색 REST 노출·클라이언트 저장 API·무결성 정책이 모두 불필요해짐 |
| SDK `resume`로 세션 관리 | `chat_sessions`·`chat_messages` DB가 세션 진실 | 다기기·재설치 복원 요구(기능 기획서 12절) |
| 서비스 JWT Bearer 가정 | 1단계부터 Bearer JWT로 전환(`D11`), 2단계에서 디바이스 세션·리프레시 추가 | 현행 필터가 쿠키만 읽는 것(지식 §1.2)을 `D11`에서 Bearer만 읽도록 바꾼다 |
| 로컬 번들 또는 원격 URL | 원격 URL 고정 | 로컬 번들은 `__Host-`·SameSite=Lax·CORS에서 구조적으로 깨짐(지식 §2.4, §4.7) |

## 2. 목표와 비목표

### 2.1 목표

| ID | 목표 | 완료 판정 |
| --- | --- | --- |
| G1 | 데스크톱 앱에서 웹과 같은 기능(GitHub 로그인, 워크스페이스, 초대, Notion 연결·동기화, 채팅, 출처)을 쓸 수 있다 | 웹 E2E 시나리오를 Electron에서 통과 |
| G2 | 백엔드 채팅 계약(SSE `chunk`/`complete`/`error`, 문서 준비 게이트, 답변·출처 저장, 세션당 스트림 1개, 30초 타임아웃)을 바꾸지 않는다 | 백엔드 `chat/` 패키지 diff 0 (LLM 어댑터 제외) |
| G3 | macOS(arm64·x64)와 Windows(x64)에 서명된 설치본을 배포하고 자동 업데이트한다 | 서명·공증 통과, 업데이트 종단 테스트 |
| G4 | `knot://` 딥링크로 초대 링크와 로그인 콜백을 앱이 받는다 | 패키징된 앱에서 콜드·웜 스타트 모두 동작 |
| G5 | Electron 공식 보안 체크리스트 20항목과 권장 Fuses를 모두 충족한다 | 8절 체크리스트 리뷰 통과 |
| G6 | 웹 사용자에게 회귀가 없다 | 웹 배포 워크플로우·E2E 무변경 통과 |
| G7 | 앱 셸 릴리스 없이 웹 배포만으로 기능 변경이 반영된다 | 셸 릴리스 주기 ≥ 4주, 웹 배포는 현행 유지 |

### 2.2 비목표

- 사용자 개인 Claude 구독으로 모델을 호출하는 것(검토 문서 8절 전제 조건이 모두 충족되기 전까지).
- 클라이언트(Electron Main)에서 LLM을 호출하거나 답변·출처를 클라이언트가 저장하는 것.
- 오프라인 사용. 원격 로드 구조의 한계이며 별도 결정으로 분리한다(지식 §5.2).
- Tauri·PWA로의 전환. 비교는 지식 §5.1에 두고 재검토 조건만 15절에 남긴다.
- 모바일, Linux 코드 서명·스토어 배포(Linux는 빌드만 제공).
- 웹에 없는 데스크톱 전용 검색·채팅 기능.

## 3. 핵심 결정 요약

| ID | 결정 | 선택 | 실제로 검토한 대안 | 선택 이유 | ADR |
| --- | --- | --- | --- | --- | --- |
| D1 | 데스크톱 셸 | **Electron 44** | Tauri 2, PWA | 웹과 동일한 Chromium 렌더링, main·preload·renderer 전부 TypeScript, Playwright 지원, 채택 사례(지식 §5.1). PWA는 트레이·글로벌 단축키·자동 시작이 없음 | 필요 |
| D2 | 콘텐츠 로드 | **원격 URL `https://knoted.kr`** | 로컬 번들(`app://`) | CORS·라우터·SSE를 웹과 동일하게 유지, 웹 배포로 즉시 반영(Slack 하이브리드 모델). 로컬 번들을 막던 쿠키 제약(`__Host-`·SameSite)은 `D11`로 사라졌으므로 전환 검토(`A12`)는 열려 있다(지식 §2.4, §4.7) | D1과 함께 |
| D3 | 모델 호출 위치 | **백엔드 유지 + Anthropic 어댑터(B안)** | D안(클라이언트 Agent SDK), C안(Workspace BYO 키) | 정책 허용 경로, 기존 `LlmClient`/`LlmStream` 추상화가 provider 교체 전제. C안은 B안 위에 후속 | 필요(검토 문서 7절) |
| D4 | 인증 | **1단계: 앱 창 GitHub 로그인 + Bearer JWT(`D11`) → 2단계: 시스템 브라우저 + 디바이스 토큰(패턴 A·B), Device flow(D)는 fallback** | 처음부터 2단계, 1단계에서 멈추기 | 1단계는 로그인 창 위치만 앱 안이고 자격증명 전달은 이미 2단계와 같은 Bearer다. RFC 8252 §8.12·1시간 만료·리프레시 부재가 상시 실행 앱에 부적합하므로(지식 §4.4) 2단계는 유지하되, 남은 차이는 **로그인 창 위치와 리프레시**뿐이다 | 필요(ADR 314 재논의) |
| D11 | 인증 자격증명 전달·저장 | **`Authorization: Bearer <JWT>` + 클라이언트 저장(웹 `localStorage`, 데스크톱 main `safeStorage`). 쿠키·CSRF 폐기** | 현행 `HttpOnly` 쿠키 유지, 쿠키+Bearer 이중 경로, 메모리 전용 저장 | 쿠키는 저장 위치를 브라우저가 정해 데스크톱이 `safeStorage`를 쓸 수 없고, `app://` 로컬 번들에서 깨진다(지식 §2.4·§4.7). 이중 경로는 필터·CSRF 매처·CORS가 두 경로를 동시에 지탱해야 한다. 메모리 전용은 새로고침마다 재로그인이라 리프레시 토큰(2단계) 없이는 못 쓴다. 대가로 `HttpOnly`의 XSS 격리를 잃는다(5.1·5.3) | 필요(ADR 314 보완) |
| D5 | 빌드·패키징 도구 | **Electron Forge 7.x** | electron-builder 26, electron-vite | Electron 공식 권장, Fuses·ASAR 무결성·서명·공증·publisher 통합, Squirrel + update.electronjs.org 무료 경로. electron-builder는 NSIS·차등 업데이트·스테이지 롤아웃·프라이빗 업데이트가 필요할 때 유리(지식 §3.2). 상세는 10절 | D1과 함께 |
| D6 | 자동 업데이트 채널 | **GitHub Releases + `update-electron-app`(update.electronjs.org)** | electron-updater + generic 서버, 자체 서버(Hazel 등) | 저장소가 공개(PUBLIC)라 무료 서비스 조건(공개 저장소 + macOS 서명) 충족. 자체 서버는 2년 이상 정체. 상세는 10절 | D5와 함께 |
| D7 | 저장소 위치 | **루트 `desktop/` 독립 pnpm 패키지, 브랜치 area `fe`** | `frontend/desktop/` 하위 패키지 | `deploy-frontend-*.yml`이 `frontend/**`를 감시하므로 분리해야 웹 배포가 불필요하게 돌지 않음. Governance area는 `be|fe`뿐이라 `fe`를 쓴다(지식 §1.6) | 불필요(메모) |
| D8 | 세션 진실 | **DB(`chat_sessions`·`chat_messages`)** | SDK 로컬 JSONL | 다기기 복원·서버 계측 요구 | 불필요(현행) |
| D9 | 프롬프트 정책·게이트·계측 | **서버 고정** | 앱 내 systemPrompt | 앱 버전별 정책 분기 방지, 인수 조건 12절 | 불필요(현행) |
| D10 | 관측 | **`electron-log` 파일 로그 + Electron `crashReporter`(수집 서버는 미결)** | Sentry Electron SDK | 개인정보·비용 결정이 필요해 15절 미결로 둠 | 필요 시 |

## 4. 시스템 아키텍처

### 4.1 구성도

```text
┌──────────────────────── 사용자 PC ────────────────────────┐
│ Electron 앱 (desktop/)                                     │
│ ┌ main (Node) ───────────────────────────────────────────┐ │
│ │ 창 관리 · 메뉴 · 트레이 · 딥링크 수신 · 업데이트 ·      │ │
│ │ 네비게이션 허용 목록 · 권한 핸들러 · 로그/크래시 ·      │ │
│ │ [2단계] 디바이스 토큰 보관(safeStorage) + Bearer 주입    │ │
│ └──────────┬───────────────── contextBridge/IPC ──────────┘ │
│ ┌ preload ─┴──────────────┐  ┌ renderer (sandbox) ────────┐ │
│ │ window.knotDesktop 노출 │  │ https://knoted.kr SPA      │ │
│ │ (최소 API, sender 검증) │  │ React 19 · axios · fetch SSE│ │
│ └─────────────────────────┘  └──────────────┬─────────────┘ │
└───────────────────────────────────────────────┼─────────────┘
                                                │ HTTPS (웹과 동일)
      ┌──────────────────┐        ┌─────────────▼──────────────┐
      │ Cloudflare Workers│        │ Spring Boot 4.1 (api.*)     │
      │ 정적 자산 · SPA   │        │ auth · workspace · chat ·   │
      │ fallback          │        │ search · notion import      │
      └──────────────────┘        │ LlmClient ─┬─ fake          │
                                   │            ├─ openai-compat │
                                   │            └─ anthropic(B안)│
                                   └──────┬───────────┬─────────┘
                                          │           │
                                   ┌──────▼─────┐ ┌───▼──────────────┐
                                   │ PostgreSQL │ │ Anthropic API     │
                                   │ + pgvector │ │ (Console 키, 서버)│
                                   └────────────┘ └──────────────────┘
```

### 4.2 프로세스별 책임

| 프로세스 | 책임 | 하지 않는 것 |
| --- | --- | --- |
| main | `BrowserWindow` 생성·복원, 애플리케이션 메뉴, 트레이, 단일 인스턴스 락, 딥링크 파싱, `will-navigate`·`setWindowOpenHandler`·`setPermissionRequestHandler`, 자동 업데이트, 로그·크래시, [2단계] 디바이스 토큰 저장·갱신·`onBeforeSendHeaders` Bearer 주입 | LLM 호출, 검색, 답변 저장, 쿠키 값 읽기(1단계에서도 `cookies.get`을 쓰지 않는다) |
| preload | `contextBridge.exposeInMainWorld('knotDesktop', …)`로 4.4의 API만 노출. CJS 단일 번들, sandbox 유지 | `ipcRenderer` 원본 노출, Node API 노출 |
| renderer | 웹 SPA 그대로. `window.knotDesktop` 존재 여부로 데스크톱을 감지해 외부 링크·딥링크·로그아웃 UX만 분기 | Electron 모듈 직접 접근 |
| 백엔드 | 현행 전부 + [B안] Anthropic 어댑터 + [2단계] 디바이스 토큰 API·Bearer 인증 | 데스크톱 전용 채팅 경로 |

### 4.3 주요 흐름

**채팅(변경 없음)**: renderer의 `streamChatMessageApi`가 `POST /api/v1/conversations/{sessionId}/messages`를 `credentials: "include"` + `X-XSRF-TOKEN`으로 호출 → 백엔드 파이프라인(게이트 → 검색 → `LlmClient` → SSE → 저장) → `complete(messageId)` → `GET /messages/{id}/sources`. Electron renderer는 Chromium이므로 fetch 스트리밍·`TextDecoder`·`parseSseEvents`가 브라우저와 동일하게 동작한다(지식 §2.5).

**로그인 1단계(패턴 C)**: SPA의 `GithubLoginButton`이 `window.location.href = {API}/oauth2/authorization/github` → main의 `will-navigate` 허용 목록(`knoted.kr`, `api.*.knoted.kr`, `github.com`)을 통과 → GitHub 로그인 → `api.*`의 콜백이 `__Host-KNOT_ACCESS_TOKEN` 쿠키를 심고 `/api/v1/auth/me`(기본값) 또는 설정된 프론트 URL로 302 → `EntryRedirect`가 분기. 세션 파티션은 `persist:knot`으로 디스크 영속.

**로그인 2단계(패턴 A·B)**: 5.2 참조.

**딥링크 초대**: 웹 초대 링크 `https://knoted.kr/invite/<token>`은 그대로 두고, 데스크톱 설치 사용자를 위해 `knot://invite/<token>`을 추가로 지원한다. main이 URL을 파싱해 `{type:'invite', token}`을 preload를 거쳐 renderer로 전달하고, SPA는 `getRouterPath({routeKey:'INVITE', params:{token}})`로 이동한다. 앱이 꺼져 있었으면 main이 링크를 보관했다가 SPA가 `getPendingDeepLink()`로 가져간다.

**업데이트**: 앱 시작 후 백그라운드 확인 → 다운로드 → "다시 시작" 안내(사용자 선택). 웹 콘텐츠는 Cloudflare 배포로 즉시 반영되므로 업데이트 대상은 셸(main·preload·Electron 런타임)뿐이다.

### 4.4 preload API 계약 (`window.knotDesktop`)

```ts
// desktop/src/shared/api.ts — preload와 SPA가 같은 타입을 공유한다
export type KnotDeepLink =
  | { type: 'invite'; token: string }
  | { type: 'chat'; workspaceId: string; sessionId?: string };

export interface KnotDesktopApi {
  readonly version: string;                 // 앱 버전(semver)
  readonly platform: 'darwin' | 'win32' | 'linux';
  readonly env: 'prod' | 'dev' | 'local';
  openExternal(url: string): Promise<void>; // https:·mailto:만 허용, 그 외 reject
  onDeepLink(handler: (link: KnotDeepLink) => void): () => void; // 구독 해제 함수 반환
  getPendingDeepLink(): Promise<KnotDeepLink | null>;
  // 2단계
  auth?: {
    startLogin(): Promise<void>;            // 시스템 브라우저를 연다
    logout(): Promise<void>;                // 폐기 API 호출 + 로컬 삭제
    onSessionChanged(handler: (state: 'signed-in' | 'signed-out') => void): () => void;
  };
  notifications?: { show(input: { title: string; body: string; link?: KnotDeepLink }): Promise<void> };
}
```

규칙:

- preload는 `ipcRenderer.invoke`/`ipcRenderer.on`을 래핑한 함수만 노출한다. `event` 객체를 renderer 콜백에 넘기지 않는다.
- main의 모든 `ipcMain.handle`은 `event.senderFrame`의 origin이 허용된 웹 오리진일 때만 처리한다(`senderFrame`이 `null`이면 거부).
- `auth.callback` 같은 로그인 콜백 데이터는 main에서만 소비하고 renderer로 보내지 않는다.
- API 추가는 이 인터페이스 파일의 변경으로만 하며, 웹 SPA는 `window.knotDesktop?.xxx` 옵셔널 접근으로 하위 호환을 지킨다(셸 업데이트가 웹 배포보다 느리다).

### 4.5 오리진·환경 정책

| 환경 | 웹 오리진(로드 URL) | API 오리진 | 비고 |
| --- | --- | --- | --- |
| prod | `https://knoted.kr` | GitHub vars `API_BASE_URL_PROD` 값(저장소에 없음). 빌드 환경변수로 주입한다(Q3) | 서명 빌드 |
| dev | `https://dev.knoted.kr` | `https://dev-api.knoted.kr` (정정 2026-09-06, `A1` 실측) | 내부 테스트 빌드 |
| local | `http://localhost:3000` | 같은 오리진(`API_MOCKING=true`, msw + devServer OAuth 302 미들웨어) | `pnpm dev`와 함께 |

- 환경은 빌드 시 상수로 고정한다(`KNOT_DESKTOP_ENV`). 런타임 전환 UI는 두지 않는다(피싱 표면).
- **API 오리진을 추정하지 않는다**(정정 2026-09-06). dev의 실제 값이 `dev-api.knoted.kr`로 확인되면서 `api.<env>.knoted.kr` 대칭 가정이 깨졌다. prod 값은 `A3` 배포 시점에 사람이 `KNOT_API_ORIGIN`으로 주입한다.
- 네비게이션 허용 목록: 웹 오리진, API 오리진, `https://github.com`(1단계 로그인), Notion OAuth 경유 도메인(`https://api.notion.com`, `https://www.notion.so` — 구현 시 실제 302 체인으로 확정). 목록 밖 URL은 `preventDefault` 후 `shell.openExternal`.
- **차단은 `will-navigate`와 `will-redirect` 두 이벤트 모두에 건다**(정정 2026-09-06, `A1`). `will-navigate`는 링크 클릭·`window.location` 변경 같은 네비게이션 *시작*에서 발화하고, 그 네비게이션 도중의 서버 302는 `will-redirect`로 발화한다. OAuth 로그인은 302 체인이므로 `will-navigate`만 막으면 허용 오리진에서 시작한 뒤 목록 밖으로 넘어가는 경로가 통과한다. `did-start-navigation`·`did-redirect-navigation`은 취소할 수 없어 **기록 전용**으로 쓴다(U2 체인 수집).
- 새 창(`window.open`, `target=_blank`)은 전부 `deny` + 검증된 `https:`만 외부 브라우저.
- CORS: 원격 로드이므로 백엔드 `AUTH_CORS_ALLOWED_ORIGINS` 변경이 없다. 2단계 Bearer 요청도 renderer 오리진이 웹 오리진이라 동일하다.

## 5. 인증 설계

### 5.1 1단계: 웹 쿠키 세션 재사용

| 항목 | 내용 |
| --- | --- |
| 동작 | 4.3의 흐름. 백엔드·SPA 변경 없음 |
| 세션 저장 | `session.fromPartition('persist:knot')`. `__Host-` 쿠키는 API 오리진의 first-party 쿠키로 저장되며 SameSite=Lax 판정도 웹과 동일(지식 §4.7) |
| Fuse | `EnableCookieEncryption` on(디스크 쿠키 암호화, macOS Keychain 사용 → 서명 필요) |
| 로그아웃 | 현재 SPA에 로그아웃 호출 코드가 없다(지식 §1.2). 데스크톱은 상시 실행이므로 메뉴에 "로그아웃"을 두고 SPA에 `POST /api/v1/auth/logout` 호출 + `/login` 이동을 추가한다(웹에도 유익) |
| 만료 | access 1시간, 리프레시 없음 → `AuthGuard`가 401에서 `/login`으로 보낸다. 데스크톱은 이 지점에서 재로그인 버튼을 눌러야 한다(GitHub이 승인 상태를 기억하면 클릭 1~2회) |
| 알려진 위험 | (1) RFC 8252 §8.12: GitHub 로그인 페이지를 앱 창(embedded user-agent)에 띄운다. GitHub의 차단 정책은 확인되지 않았고 Google은 차단한다(지식 §4.3). (2) 앱 창에서는 브라우저의 GitHub 세션을 공유하지 못해 매번 로그인한다. (3) WebAuthn(패스키) 로그인이 제한될 수 있다 |
| 수용 조건 | 2단계 착수를 전제로 한 임시 경로. 출시 전 GitHub 로그인이 Electron 창에서 실제로 동작하는지(경고·차단 여부)를 dev 빌드로 확인한다 |

### 5.2 2단계: 시스템 브라우저 로그인 + 디바이스 토큰

목표: 앱 안에서 GitHub 자격증명을 입력하지 않고, 1시간마다 재로그인하지 않으며, 기기별로 세션을 관리·폐기할 수 있게 한다.

**시퀀스**

```text
앱(main)                      시스템 브라우저                 백엔드(api.*)                     GitHub
  │ verifier·state 생성           │                              │                                │
  │ loopback 127.0.0.1:P 열기     │                              │                                │
  │─ openExternal(API/oauth2/authorization/github?client=desktop │                                │
  │     &code_challenge=S256(v)&state=s&return=loopback:P) ─────▶│ resolver가 attributes에 보관     │
  │                               │◀──── 302 github.com/login/oauth/authorize ──────────────────▶│
  │                               │      사용자 로그인·승인(브라우저 세션·패스키 사용 가능)         │
  │                               │──── /login/oauth2/code/github?code&state ───▶│               │
  │                               │      성공 핸들러: client=desktop → 쿠키 미발급,               │
  │                               │      일회용 device_code 발급(TTL 120s, S256 challenge 바인딩)  │
  │◀──── 302 http://127.0.0.1:P/callback?code=dc&state=s ◀───────│                                │
  │  (fallback: knot://auth/callback?code=dc&state=s)            │                                │
  │ state 검증, 포트 닫기         │  "앱으로 돌아가세요" 페이지    │                                │
  │─ POST /api/v1/auth/device/token {code: dc, code_verifier: v, device: {name, platform}} ──────▶│
  │◀─ {access_token(JWT 1h), refresh_token(opaque), expires_in} ─│                                │
  │ refresh는 safeStorage 암호화 후 userData 파일, access는 메모리 │                                │
  │ onBeforeSendHeaders: API 오리진 요청에만 Authorization: Bearer 주입 │                          │
```

**API 계약(초안)**

| 메서드 | 경로 | 요청 | 응답 | 오류 |
| --- | --- | --- | --- | --- |
| GET | `/oauth2/authorization/github?client=desktop&code_challenge=…&code_challenge_method=S256&state=…&return=loopback:{port}` | 쿼리 | 302 → GitHub | `return`·`challenge` 형식 오류 400 |
| (콜백) | `/login/oauth2/code/github` | Spring 기본 | `client=desktop`이면 302 → `http://127.0.0.1:{port}/callback?code&state` 또는 `knot://auth/callback?code&state` | 실패 시 `knot://auth/callback?error=…` |
| POST | `/api/v1/auth/device/token` | `{code, code_verifier, device:{name, platform, appVersion}}` | `{accessToken, refreshToken, expiresIn, session:{id, deviceName}}` | 코드 만료·재사용·verifier 불일치 → 400 `DEVICE_CODE_INVALID`, 재사용 감지 시 연관 토큰 폐기 |
| POST | `/api/v1/auth/device/refresh` | `{refreshToken}` | 새 access + **새 refresh**(rotation), 이전 refresh 즉시 무효 | 재사용 감지 → family 전체 폐기, 401 |
| POST | `/api/v1/auth/device/revoke` | `{refreshToken}` 또는 Bearer로 현재 세션 | 200(무효 토큰이어도 200, RFC 7009) | — |
| GET | `/api/v1/auth/sessions` | Bearer 또는 쿠키 | 기기 목록 `{id, deviceName, platform, createdAt, lastUsedAt, current}` | — |
| DELETE | `/api/v1/auth/sessions/{id}` | 〃 | 204 | CORS 허용 메서드에 DELETE가 없으므로 웹에서 쓰려면 `SecurityConfig` CORS 메서드 추가 필요 |

**토큰 규격**

- access: 기존 HS256 키·issuer·audience 재사용, `typ=DEVICE_ACCESS`, `sid=<device_session_id>`, 만료 1시간(`auth.jwt.expiration`). 기존 `JwtProvider`의 타입 열거(`ACCESS`, `ONBOARDING`)에 추가.
- refresh: 256-bit 난수 opaque. DB에는 해시만(ADR 254의 HMAC·AES-GCM 관행 재사용), 6개월 미사용 시 만료, rotation + family 폐기(RFC 9700 §4.14.2).
- device_code: 256-bit 난수, TTL 120초, 1회용, `code_challenge`·`state`·`return`과 함께 세션(또는 서명 쿠키)에 보관.

**데이터**: Flyway `V14__create_device_sessions.sql` — `device_sessions(id, member_id, device_name, platform, app_version, refresh_token_hash, family_id, created_at, last_used_at, expires_at, revoked_at)` + `device_authorization_codes`(또는 인메모리 TTL 캐시, 단일 인스턴스 전제는 ADR 212·328과 동일).

**Spring Security 변경**(지식 §4.6)

- 기존 `JwtAuthenticationFilter`(쿠키) 유지 + `oauth2ResourceServer(jwt)` 병행. `NimbusJwtDecoder.withSecretKey(key).macAlgorithm(HS256)` + `typ=DEVICE_ACCESS`·`sid` 유효성(폐기 여부) 검증기.
- CSRF: `requireCsrfProtectionMatcher`를 "기본 매처 AND NOT(`Authorization` 헤더 존재)"로 바꿔 Bearer 요청만 면제. 쿠키 요청은 현행 유지.
- `OAuth2AuthorizationRequestResolver` 커스터마이즈로 `client`·`code_challenge`·`return`을 attributes에 보관, 커스텀 `AuthenticationSuccessHandler`가 `client=desktop` 분기. 이 분기는 ADR 314("백엔드는 고정 리다이렉트 3개")의 재논의 조건("로그인 이후 목적지가 외부에서 지정되는 흐름 추가 시")에 해당하므로 ADR 314를 보완하는 ADR로 기록한다.
- `state`에는 URL이나 민감 정보를 넣지 않고 서버 저장 키만 쓴다(open redirector 방지). `return`은 `loopback:{port}`·`deeplink` 두 값만 허용한다.

**Electron 측**

- loopback을 1차, 딥링크를 2차로 둔다. loopback은 요청 시작 시 임의 포트에 `127.0.0.1`만 바인딩하고 응답 직후 닫는다(RFC 8252 §8.3). 딥링크 스킴은 `knot`(단순) 또는 `kr.knoted.app`(reverse-domain, OAuth 2.1 권고) 중 하나를 D4 ADR에서 정한다.
- 토큰 저장: `safeStorage.encryptString` → `userData/auth.bin`. `isEncryptionAvailable()`이 false(Linux `basic_text`)면 저장하지 않고 매 실행 로그인.
- Bearer 주입: `session.webRequest.onBeforeSendHeaders({urls:[`${API_ORIGIN}/*`]})`에서만. 다른 오리진에는 절대 붙이지 않는다.
- 만료 5분 전 백그라운드 refresh, 실패 시 `signed-out` 이벤트 → SPA가 `/login`으로.
- 1단계 쿠키 경로와 2단계 Bearer 경로가 공존하는 동안 SPA는 어느 쪽인지 알 필요가 없다(백엔드 필터가 Bearer 우선).

### 5.3 위협 모델·완화

| 위협 | 완화 |
| --- | --- |
| 딥링크 스킴 탈취(다른 앱이 `knot://` 등록) | 코드 1회용·TTL 120초, S256 verifier 바인딩(코드만으로 교환 불가), 앱 내 state 매칭, loopback 1차 |
| loopback 포트 가로채기 | 동일(PKCE), 응답 즉시 포트 닫기, `127.0.0.1` 전용 바인딩 |
| renderer XSS | sandbox·contextIsolation·최소 preload API·sender 검증. 2단계에서는 토큰이 main에만 있어 유출은 막지만 요청 위조는 쿠키와 동일하게 가능 → 서버 측 검증·CSRF(쿠키)·rate limit 유지 |
| 토큰 파일 탈취 | macOS Keychain·Windows DPAPI(같은 계정의 다른 앱은 복호화 가능 → 위협 모델에 명시), refresh rotation·재사용 감지·기기 목록 폐기 |
| 피싱(가짜 로그인 창) | 앱 안에서 자격증명을 받지 않음(2단계), 환경 전환 UI 없음, 서명된 배포본 |
| 백엔드 리다이렉트 오용 | `return` 값 화이트리스트, `state`에 URL 미포함 |

## 6. 채팅·LLM

### 6.1 데스크톱 관점

변경 없음. 세션당 스트림 1개(인메모리 레지스트리)·30초 SSE 타임아웃·`CHAT_DOCUMENTS_NOT_READY` 게이트·`search_references` 저장은 전부 서버가 유지한다. 데스크톱 퀵 질문 창(7절 P2)도 같은 API를 같은 세션 쿠키·토큰으로 호출한다.

### 6.2 백엔드 Anthropic 어댑터(B안, Electron과 독립)

| 항목 | 설계 |
| --- | --- |
| 활성화 | `llm.chat.provider=anthropic` 분기를 `LlmClientConfig`에 추가. 임베딩은 `llm.embedding.provider=openai-compatible`(Qwen) 그대로 둔다. provider 키 분리와 HTTP 클라이언트 빈 분리는 `B0`에서 끝났다(정정 2026-09-06, 지식 §1.4) |
| 설정 | `llm.anthropic.api-key`(`ANTHROPIC_API_KEY`), `llm.anthropic.model`(기본 `claude-opus-5`), `llm.anthropic.effort`(기본 `medium`, 채팅 QA 측정 후 조정), `llm.anthropic.max-tokens`(기본 4096), `llm.anthropic.request-timeout`(PT30S — SSE 타임아웃과 정합), `llm.anthropic.research-loop.enabled`(기본 false) |
| 클래스 | `chat/infrastructure/anthropic/AnthropicLlmClient implements LlmClient`, `AnthropicLlmStream implements LlmStream`(pull형: `hasNext`/`next`가 `content_block_delta.text_delta`만 돌려주고 `message_stop`에서 종료), `AnthropicRequestMapper`(`SearchContext.groundingPrompt` → `system`, 히스토리 → `messages`) |
| HTTP | 옵션 1: `com.anthropic:anthropic-java`(`client.messages().createStreaming`) 도입. 옵션 2: 기존 JDK `HttpClient`로 `POST /v1/messages`(`x-api-key`, `anthropic-version`, `stream:true`) 직접 호출 + SSE 파서 재사용. 구현 Issue에서 결정(검토 문서 7절) |
| 파라미터 | `thinking` 생략(Opus 5 기본 adaptive) 또는 `{type:"adaptive"}`, `output_config.effort`. `temperature`는 넘기지 않는다(Opus 5에서 400). assistant prefill 금지 |
| 캐시 | `system`을 `[근거 규칙(cache_control ephemeral)] + [근거 문서]` 두 블록으로 나눠 고정 부분만 캐시 |
| 오류 매핑 | 401/403 → `LLM_CONFIGURATION_INVALID`, 429·529(overloaded) → `LLM_RATE_LIMITED`(SSE `error` + `Retry-After`), 타임아웃 → 기존 `LLM_STREAM_TIMEOUT`, `stop_reason=refusal` → 기존 "정보 없음" 정책 문구가 아닌 별도 코드 `LLM_REFUSED`로 사용자에게 안내(`stop_details.category` 로그) |
| 계측 | 기존 단계별 시간 기록에 `usage.input_tokens`·`output_tokens`·`cache_read_input_tokens`를 추가 저장(비용 관측) |
| 재검색 루프(선택) | `research-loop.enabled=true`면 `search_knowledge` 툴을 정의하고 `stop_reason=tool_use` 시 서버가 `PublishedDocumentSearchService`를 실행, `tool_result`를 한 user 메시지로 반환, 최대 2회. 기본 off — 왕복 2회로 TTFT가 늘어나므로(검토 문서 5.6절) 실측 후 결정 |
| 품질 재검증 | 모델 교체 전 `docs/llm-search-benchmark-independent-30.json` gold set 30문항 + 사람 검수, TTFT 5초 실측(ADR 271 조건). 통과 전 `llm.chat.provider=anthropic`을 운영에 켜지 않는다 |
| 비용 가드 | 질문당 입력 상한(근거 문서 10,000자 유지), 세션당 히스토리 상한(현행 4개 메시지·4,000자), 일일 토큰 예산 로그 경고 |

### 6.3 C안(Workspace BYO 키) 후속

B안이 안정되면 `content_source_authorizations`와 같은 암호화 envelope으로 Workspace별 Anthropic 키를 보관하고 `AnthropicLlmClient`가 Workspace 키를 우선 사용한다. 키 등록·폐기 UI가 필요하므로 별도 Issue·ADR.

## 7. 데스크톱 기능 로드맵

| 단계 | 범위 | 완료 조건 |
| --- | --- | --- |
| **P0 스파이크(1~2주)** | `desktop/` 생성, Forge 스캐폴딩, `https://dev.knoted.kr` 로드, 보안 기본값·Fuses, 네비게이션 허용 목록, 메뉴·외부 링크, macOS 임시 서명, dev 빌드 배포 | GitHub 로그인·Notion OAuth·채팅 SSE가 Electron 창에서 동작함을 확인. GitHub 로그인 차단·경고 여부 기록 |
| **P1 MVP** | P0 + 창 상태 복원, 로그아웃 메뉴(SPA 로그아웃 호출 추가), `electron-log`·crashReporter, Playwright Electron 스모크, 3-OS CI 빌드, macOS 서명·공증, Windows 서명(또는 미서명 결정), GitHub Releases + 자동 업데이트, 다운로드 안내 페이지 | G1·G2·G3·G5·G6·G7 |
| **P2 데스크톱 통합** | `knot://` 딥링크(초대·채팅), 2단계 인증(디바이스 토큰), 트레이·글로벌 단축키 퀵 질문 창(작은 `BrowserWindow`에 `/workspace/:id/chat` 로드), Notion 동기화 완료 알림(앱이 `GET /api/v1/imports/{id}`를 폴링), Dock 배지 | G4, 2단계 인증 ADR Accepted |
| **P3 선택** | 기기 목록·원격 로그아웃 UI(웹 공용), 로컬 번들(`app://`) 셸로 시작 속도 개선(2단계 인증 전제), 원격 MCP 서버(개발자용, 지식 §5.4), C안 BYO 키 | 각각 별도 Issue·ADR |

각 단계의 실제 작업 ID·상태·선행·게이트는 [로드맵](./electron-desktop-app-roadmap.md)이 정본이다. 이 절은 단계의 의미와 범위만 설명한다.

P0의 목적은 D1·D2·D4-1단계의 전제(Electron 창에서 GitHub·Notion OAuth 체인이 동작하는가)를 코드로 확인하는 것이다. 실패하면 2단계 인증을 P1로 당긴다.

## 8. 보안 요구사항 체크리스트 (구현·리뷰 게이트)

Electron 공식 Security 문서 20항목(지식 §2.3)을 Knot 값으로 고정한다. PR 리뷰에서 이 표를 그대로 확인한다.

| # | 항목 | Knot 값 |
| --- | --- | --- |
| 1 | HTTPS만 로드 | 4.5 오리진 표. `http://localhost:3000`은 `local` 빌드에서만 |
| 2 | `nodeIntegration: false` | 기본값 유지, 모든 창 |
| 3 | `contextIsolation: true` | 기본값 유지 |
| 4 | sandbox | `app.enableSandbox()`로 전체 강제 |
| 5 | 권한 요청 핸들러 | 웹 오리진에서 온 `notifications`·`clipboard-read`·`clipboard-sanitized-write`만 허용, 나머지 거부 |
| 6 | `webSecurity` | 기본 true |
| 7 | CSP | 웹 응답 헤더가 담당(웹 측 작업 `W1`~`W3`의 `W3`). Cloudflare Workers 정적 자산의 `_headers`로 `Content-Security-Policy`를 넣는다. 정적 자산 응답은 요청마다 nonce를 만들 수 없으므로 Emotion 인라인 스타일은 `style-src 'unsafe-inline'`으로 허용한다. 지시문은 9.4를 정본으로 한다. 앱은 `onHeadersReceived`로 CSP를 완화하지 않는다 |
| 8~10 | `allowRunningInsecureContent`·`experimentalFeatures`·`enableBlinkFeatures` | 사용 금지 |
| 11~12 | `<webview>` | `webviewTag: false`(기본), `will-attach-webview`에서 전부 거부 |
| 13 | 네비게이션 제한 | `will-navigate` + `will-redirect` 허용 목록 차단(둘 다 `preventDefault` 가능), 목록 밖은 외부 브라우저. `did-start-navigation`·`did-redirect-navigation`은 취소 불가이므로 기록 전용 (정정 2026-09-06) |
| 14 | 새 창 제한 | `setWindowOpenHandler` → `deny` |
| 15 | `shell.openExternal` | `https:`·`mailto:`만, 문자열 검증 후 |
| 16 | Electron 버전 | 44 시작, 메이저 1개씩 8주 주기 추적, EOL 전 업그레이드(Renovate 등록) |
| 17 | IPC sender 검증 | 모든 `ipcMain.handle`에서 `senderFrame` origin 검사 |
| 18 | `file://` 미사용 | 원격 로드. Fuse `GrantFileProtocolExtraPrivileges` off |
| 19 | Fuses | `RunAsNode` off, `EnableNodeOptionsEnvironmentVariable` off, `EnableNodeCliInspectArguments` off, `EnableCookieEncryption` on, `EnableEmbeddedAsarIntegrityValidation` on, `OnlyLoadAppFromAsar` on, `GrantFileProtocolExtraPrivileges` off |
| 20 | API 비노출 | 4.4 인터페이스 외 노출 금지, `ipcRenderer` 원본 금지 |
| + | 환경변수·플래그 | `disable-site-isolation-trials`·`--ignore-certificate-errors` 사용 금지 |
| + | 자격증명 | 1단계: main이 쿠키 값을 읽지 않는다. 2단계: 토큰은 main·safeStorage에만, 로그·크래시 리포트에 포함 금지 |
| + | 의존성 | `pnpm audit` CI, Electron·Forge·`update-electron-app`만 런타임 의존성. keytar 금지(archived) |

## 9. 프로젝트 구조·기술 스택

### 9.1 `desktop/` 패키지

```text
desktop/
├─ package.json            # name: knot-desktop, type: module, engines.node >=22, packageManager pnpm
├─ pnpm-workspace.yaml     # nodeLinker: hoisted (Electron·Forge 공식 요구. pnpm 11은 이 파일에서 설정을 읽고 워크스페이스 루트 단위로 적용되므로 desktop/을 별도 루트로 둔다)
├─ forge.config.ts         # packagerConfig(asar, protocols, osxSign/osxNotarize, icon), makers, plugins(fuses), publishers(github)
├─ tsconfig.json           # strict, module NodeNext, 별도 tsconfig.preload.json(CJS)
├─ src/
│  ├─ main/
│  │  ├─ index.ts          # app lifecycle, single instance, whenReady
│  │  ├─ windows.ts        # BrowserWindow 생성·상태 복원, 퀵 질문 창
│  │  ├─ navigation.ts     # 허용 목록, will-navigate, setWindowOpenHandler, permission handler
│  │  ├─ deepLink.ts       # knot:// 파서(순수 함수) + 등록·수신
│  │  ├─ menu.ts, tray.ts, updater.ts, logging.ts, crash.ts
│  │  └─ auth/             # 2단계: loopback 서버, pkce, tokenStore(safeStorage), bearerInjector
│  ├─ preload/index.ts     # contextBridge.exposeInMainWorld('knotDesktop', …)
│  ├─ shared/              # api.ts(4.4 타입), env.ts(오리진 표), deepLink 타입
│  └─ renderer/            # 없음(원격 로드). 로컬 오류 페이지(offline.html)만
├─ resources/              # 아이콘(icns/ico/png), entitlements.plist
├─ test/                   # vitest(main 순수 함수), playwright(electron 스모크)
└─ README.md
```

- 빌드: main·preload는 TypeScript → esbuild 번들을 Forge `hooks.generateAssets`(또는 `prePackage`)에서 실행한다. Forge `plugin-webpack`/`plugin-vite`는 renderer 엔트리를 전제하므로 쓰지 않는다. renderer 빌드는 없다(원격). 로컬 `offline.html`만 정적 포함.
- 웹 SPA(`frontend/`)와 코드 공유는 `desktop/src/shared/api.ts`의 타입 파일 하나뿐이다. SPA는 이 타입을 복사해 `src/shared/types/desktop.ts`로 두고 `declare global { interface Window { knotDesktop?: KnotDesktopApi } }`를 선언한다(패키지 간 import는 두 프로젝트의 lockfile을 얽히게 하므로 피한다).
- 린트·포맷은 `frontend/eslint.config.js`를 참조해 동일 규칙을 복사한다. `frontend/.claude/rules`는 React 규칙이므로 `desktop/`에는 별도 `CLAUDE.md`(main 프로세스 규칙)를 둔다.

### 9.2 의존성(버전은 지식 §2.1·§3 참조, 구현 시 최신 확인)

| 구분 | 패키지 |
| --- | --- |
| 런타임 | `electron@44`, `update-electron-app`, `electron-log`, `electron-squirrel-startup`(Windows Squirrel 설치·업데이트 이벤트) |
| 빌드 | `@electron-forge/cli@7`(7.11.2 안정, 8은 alpha), `@electron-forge/maker-{dmg,zip,squirrel,deb}`, `@electron-forge/plugin-fuses`, `@electron-forge/publisher-github`, `@electron/fuses`, `@electron/notarize`, `typescript@7`, `esbuild`. `packagerConfig.asar: true`를 명시한다(Forge 7 기본 off) |
| 테스트 | `vitest`, `@playwright/test`(`_electron`) |
| 금지 | `keytar`, `electron-remote`류, 클라이언트 LLM SDK(`@anthropic-ai/claude-agent-sdk`, `@anthropic-ai/sdk`) |

### 9.3 웹 SPA(`frontend/`) 변경 목록

| 변경 | 단계 | 비고 |
| --- | --- | --- |
| `window.knotDesktop` 타입 선언·감지 훅(`useDesktop`) | P1 | `src/shared/hooks/`에 두고 `hook-guide.md` 준수 |
| 외부 링크(Notion 페이지 링크 등)를 데스크톱에서 `openExternal`로 | P1 | `target=_blank`는 main이 가로채므로 필수는 아님. UX 통일용 |
| 로그아웃 액션(`POST /api/v1/auth/logout` → `/login`) | P1 | 웹에도 필요한 누락 기능 |
| 딥링크 수신 → 라우터 이동 | P2 | `RouterProvider` 상위에서 `onDeepLink` 구독 |
| 초대 페이지에 "앱에서 열기"(`knot://invite/<token>`) | P2 | 앱 미설치 시 안내 |
| 다운로드 페이지(`/download`, OS 감지) | P1 | 정적 라우트 |
| `<title>` 수정(`Document` → `Knot`) | P1 | 창 제목에 그대로 보인다 |
| CSP 헤더(`_headers`) | P1 병행 | 웹 보안 Issue로 분리 |

### 9.4 웹 CSP 헤더(`_headers`)

Cloudflare Workers 정적 자산이 읽는 `_headers`를 빌드 산출물(`frontend/dist`)에 함께 만들어 모든 경로(`/*`)에 아래 지시문을 붙인다.

| 지시문 | 값 | 이유 |
| --- | --- | --- |
| `default-src` | `'self'` | 아래에서 따로 열지 않은 것은 같은 오리진만 |
| `script-src` | `'self'` | 번들 1개만 실행한다. 인라인 스크립트·`eval` 없음 |
| `style-src` | `'self' 'unsafe-inline' https://cdn.jsdelivr.net` | Emotion이 런타임에 `<style>`을 만들고, 정적 응답에는 요청별 nonce를 넣을 수 없다. Pretendard CSS는 jsDelivr에서 온다(`index.html`) |
| `font-src` | `'self' https://cdn.jsdelivr.net` | 위 CSS가 참조하는 woff2 |
| `img-src` | `'self' data: https:` | GitHub 프로필·Notion 이미지의 호스트가 고정되지 않는다 |
| `connect-src` | `'self' {API 오리진}` | 프론트와 API가 크로스 오리진(지식 §1.1). 값은 빌드 시 `API_BASE_URL`에서 넣고 저장소에 고정하지 않는다(로드맵 Q3·Q11) |
| `frame-ancestors` | `'none'` | 클릭재킹 차단 |
| `base-uri` | `'self'` | `<base>` 주입으로 번들 경로를 바꾸지 못하게 한다 |
| `form-action` | `'self'` | 폼 전송 목적지 고정 |
| `object-src` | `'none'` | 플러그인 미사용 |

`_headers`는 배포 산출물에만 필요하므로 저장소에 정적 파일로 두지 않고 웹팩 빌드가 만든다. 개발 서버(`pnpm dev`)와 vitest·Playwright는 이 헤더를 받지 않는다.

## 10. 빌드·패키징·서명·배포·업데이트

(지식 §3 조사 결과 기준. 세부 버전·명령은 지식 문서를 따른다.)

### 10.1 파이프라인

```text
tag desktop-v0.1.0 ─▶ GitHub Actions matrix(macos-latest arm64/x64, windows-latest x64, ubuntu-latest x64)
  ├─ pnpm install --frozen-lockfile (electron 바이너리 캐시 ~/.cache/electron)
  ├─ pnpm test (vitest) · pnpm test:e2e (playwright electron, 미패키징 빌드, ubuntu/macos)
  ├─ pnpm make  (Forge: asar + fuses + osxSign + osxNotarize / windows-sign / deb·zip)
  └─ pnpm publish (publisher-github: draft Release에 dmg/zip/exe/nupkg/RELEASES/deb 업로드)
사람: Release 노트 확인 → publish → update.electronjs.org가 새 버전을 앱에 안내
```

### 10.2 결정·확인 항목

| 항목 | 결정(초안) | 확인 필요 |
| --- | --- | --- |
| 서명 계정(macOS) | 팀 명의 Apple Developer Program(US$99/년) 1개, Developer ID Application 인증서, App Store Connect API 키로 `notarytool` 공증(2FA 불필요). Hardened Runtime + `com.apple.security.cs.allow-jit`만 | 계정 소유 주체(개인/팀), `.p12` base64 secret 보관·회전, `@electron/notarize` 자동 staple 여부(`xcrun stapler validate`로 검증) |
| 서명(Windows) | (a) Azure Artifact Signing Basic US$9.99/월(조직 계정 필요, 개인 개발자 불가) 또는 (b) 미서명 출시 + SmartScreen "Run anyway" 안내. EV 인증서는 2024년부터 SmartScreen 이점이 없어 제외 | 팀이 조직 계정을 가질 수 있는지, 한국 조직 자격(문서 불일치, 지식 §3.3) |
| 업데이트 | `update-electron-app` 3.x + GitHub Releases(update.electronjs.org 조건: 공개 저장소 + macOS 서명 — 충족). 체크 간격 기본 10분, 다운로드 후 재시작 다이얼로그 | Windows는 Squirrel 설치본(`Setup.exe` + `RELEASES`), 설치 직후 firstrun 중 체크 10초 지연 |
| 아키텍처 | macOS arm64·x64 두 벌(arm64 러너에서 교차 패키징, 네이티브 모듈 없음), Windows x64 | universal(2배 크기)로 합칠지 |
| Linux | deb·zip 빌드만, 서명·자동 업데이트 없음(내장 autoUpdater가 Linux 미지원) | — |
| asar·Fuses | `asar: true` + 8절 Fuse 세트. Playwright E2E는 미패키징 빌드로 | 지식 §3.6 |
| pnpm | `desktop/pnpm-workspace.yaml`에 `nodeLinker: hoisted` | 지식 §3.2 |
| 버전 | `desktop-v{semver}` 태그, 셸 버전은 웹과 독립. GitHub Release prerelease 플래그로 베타 채널 | `update-electron-app`의 draft/prerelease 처리 재확인 |
| 라이선스 | `LICENSE`·`LICENSES.chromium.html` 동봉 + About 창 오픈소스 고지, Electron MIT | 앱 자체 npm 의존성 고지 생성 |
| CI 비용 | macOS 러너 US$0.062/분(≈10.33배), `macos-latest`는 macOS 26 arm64, 공증 `--wait` 동기 대기 → 잡 timeout 60분 | 공개 저장소는 표준 러너 무료(지식 §3.5) |

### 10.3 릴리스 절차(초안)

1. `develop`에 `desktop/` 변경 병합(FE PR 규칙 동일).
2. `desktop/package.json` 버전 올림 + 태그 `desktop-vX.Y.Z` push → 빌드·서명·draft Release.
3. dev 빌드로 스모크(로그인·채팅·업데이트 감지) 후 Release publish.
4. 문제 시 롤백은 이전 빌드를 더 높은 버전 번호로 재게시한다(Squirrel은 버전 비교 기반이라 Release 삭제만으로는 이미 설치된 앱이 되돌아가지 않는다). 핫픽스는 새 태그로.

## 11. 테스트 전략

| 계층 | 도구 | 대상 |
| --- | --- | --- |
| 단위(main) | vitest | 딥링크 파서, 허용 목록 판정, PKCE 생성, 토큰 저장소(safeStorage 모킹), 업데이트 상태 머신 |
| 스모크(앱) | Playwright `_electron.launch`(experimental) | 창 생성·로드 URL·메뉴·외부 링크 거부·`open-url` 이벤트 주입(`electronApp.evaluate`로 `app.emit('open-url', …)`)·IPC sender 거부. 미패키징 빌드에서 실행한다(프로덕션 Fuse `EnableNodeCliInspectArguments: false`는 Playwright 실행을 막음). 패키징본의 Fuse는 `npx @electron/fuses read`로 별도 검증 |
| E2E(기능) | Playwright Electron + 웹 dev 서버(`API_MOCKING=true`, `local` 빌드) | 기존 `frontend/src/__test__` 시나리오 중 로그인·초대·채팅을 Electron에서 재실행(msw + devServer 302 미들웨어 그대로) |
| 인증 종단 | 수동(dev 빌드) | 실제 GitHub·Notion OAuth 체인, 2단계 loopback·딥링크·refresh·폐기 |
| 보안 리뷰 | 체크리스트(8절) | PR 템플릿 항목 |
| 백엔드 | 기존 `test`/`integrationTest`/`acceptanceTest` | Anthropic 어댑터(WireMock SSE), 디바이스 토큰(rotation·재사용·폐기·CSRF 면제·쿠키 경로 회귀) |

## 12. 관측·운영·지원

- 로그: `electron-log`로 `app.getPath('logs')`에 회전 저장. 토큰·쿠키·개인정보 출력 금지. "로그 폴더 열기" 메뉴.
- 크래시: `crashReporter.start({uploadToServer: <미결>})`. 수집 서버(자체 vs Sentry)는 개인정보 고지와 함께 15절 결정.
- 버전·환경 표시: About 창에 앱 버전·Electron·Chromium·환경.
- 지원 창구 분리: "답변이 안 나온다"는 서버 로그로 판단 가능(모델 호출이 서버에 있으므로 검토 문서 5.7절의 분산 문제가 없다). 앱 문제는 로그 파일 첨부.
- 업데이트 강제: 셸 최소 버전을 SPA가 `knotDesktop.version`으로 확인해 너무 오래된 셸에 업데이트 안내(`semver` 비교, 차단은 하지 않음).
- 백엔드 운영: 디바이스 세션 테이블 정리 배치(만료·폐기 90일 후 삭제), Anthropic 사용량·비용 대시보드(usage 로그 기반).

## 13. 백엔드 변경 목록

| 변경 | 단계 | 위험 신호 | 비고 |
| --- | --- | --- | --- |
| 채팅·임베딩 provider 설정 분리(`llm.chat.provider`, `llm.embedding.provider`) | B안 선행 | `shared` | 기존 `openai-compatible` 동작 불변 |
| `AnthropicLlmClient`/`AnthropicLlmStream` + 설정 키 + 오류 코드 | B안 | `external`, `shared` | ADR(검토 문서 7절 B vs C) |
| 사용량 계측 컬럼(입력·출력·캐시 토큰) | B안 | `data` | Flyway |
| `device_sessions`·인가 코드 저장, 디바이스 토큰 API 5종 | 2단계 | `security`, `data`, `cross-boundary`, `core-flow` | 인터뷰 + Grill + ADR(314 보완) |
| 리소스 서버 JWT 병행, CSRF 매처, OAuth resolver·성공 핸들러 분기 | 2단계 | `security` | 동일 ADR |
| CORS 허용 메서드에 DELETE(기기 세션 삭제를 웹에서 쓸 때) | P3 | `security` | 없어도 POST 대체 가능 |
| 로그아웃 성공 핸들러(302 대신 204) | P1 | `cross-boundary` | SPA가 XHR로 호출하므로 |
| 원격 MCP 서버(Spring AI) | P3 선택 | `external`, `security` | 별도 기획 |

변경하지 않는 것: 채팅 SSE 계약, 검색 서비스, 답변·출처 저장, 문서 준비 게이트, 프롬프트 정책, 세션 모델, Notion 연결.

## 14. Issue 분할 초안 (공통 Issue 계약 기준)

각 행은 `$knot-issue-planning` 입력 후보다. 위험 신호가 하나라도 있으면 Harnessed 경로(인터뷰 → Grill → ADR 판정)이며, 본문은 `구현 기능 설명`·`TODO`·`메모` 3섹션만 쓴다. 대안 열은 "팀이 실제로 검토한 것"만 적었고, 인터뷰에서 확인되지 않으면 ADR을 만들지 않는다.

| # | 제목(초안) | area | 단계 | 위험 신호 | 인터뷰/Grill | ADR 후보(실제 대안) | 의존 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| I1 | `[FE] 데스크톱 앱 스파이크: Electron 셸에서 knoted.kr 원격 로드·OAuth 체인 검증` | fe | P0 | `shared`, `core-flow` | 필요 | D1(Electron vs Tauri vs PWA), D2(원격 vs 로컬) | — |
| I2 | `[FE] 데스크톱 앱 셸 MVP: 창·메뉴·네비게이션 제한·보안 기본값·로그` | fe | P1 | `shared`, `security` | 필요 | I1의 ADR 참조 | I1 |
| I3 | `[FE] 데스크톱 빌드·서명·공증·릴리스 파이프라인` | fe | P1 | `external`, `hard-to-reverse`, `shared` | 필요 | D5(Forge vs builder), D6(업데이트 채널), 서명 계정 | I2 |
| I4 | `[FE] 웹 로그아웃 액션과 데스크톱 감지 훅` | fe | P1 | `cross-boundary` | 필요(짧게) | 없음(메모) | — |
| I5 | `[FE] 웹 CSP 헤더(Cloudflare _headers)` | fe | P1 병행 | `security` | 필요 | nonce vs unsafe-inline | — |
| I6 | `[BE] 로그아웃 응답 204 및 XHR 호환` | be | P1 | `cross-boundary` | 필요(짧게) | 없음 | I4 |
| I7 | `[BE] 채팅 LLM Anthropic Messages API 어댑터` | be | B안 | `external`, `shared` | 필요 | B안 vs C안(검토 문서 7절), SDK vs HttpClient | provider 분리 |
| I8 | `[BE] 채팅·임베딩 provider 설정 분리` | be | B안 선행 | `shared` | 필요(짧게) | 없음 | — |
| I9 | `[BE] 데스크톱 디바이스 토큰 인증 경로(코드 교환·refresh rotation·폐기·기기 목록)` | be | P2 | `security`, `data`, `cross-boundary`, `core-flow` | 필요(인터뷰 6항목 전부) | D4(패턴 A·B vs C vs D), ADR 314 보완 | I2 |
| I10 | `[FE] 데스크톱 시스템 브라우저 로그인·loopback·딥링크 콜백·토큰 저장` | fe | P2 | `security`, `cross-boundary` | 필요 | I9와 같은 ADR | I9 |
| I11 | `[FE] knot:// 딥링크: 초대·채팅 진입` | fe | P2 | `core-flow` | 필요 | 스킴 이름(`knot` vs reverse-domain) | I2 |
| I12 | `[FE] 트레이·글로벌 단축키 퀵 질문 창` | fe | P2 | `core-flow` | 필요(짧게) | 없음 | I2 |
| I13 | `[FE] Notion 동기화 완료 알림(폴링)` | fe | P2 | 없음 → Lightweight 가능 | 판정기 결과 따름 | 없음 | I2 |
| I14 | `[FE] 기기 목록·원격 로그아웃 UI` | fe | P3 | `security` | 필요 | 없음 | I9 |

Issue 생성은 사용자가 명시적으로 허용하고 판정기가 `pass`·`publish_ready=true`를 반환한 경우에만 `--publish`로 수행한다. 이 표는 초안일 뿐 snapshot이 아니다.

`I1~I14`는 이 문서의 초안 번호다. 실제 실행 단위·상태·의존은 [로드맵](./electron-desktop-app-roadmap.md) 4절의 `A*`·`W*`·`B*` ID를 쓰며, 대응표는 로드맵 4절에 있다.

## 15. 리스크와 미결 결정

| # | 리스크·미결 | 영향 | 해소 방법·담당 |
| --- | --- | --- | --- |
| R1 | GitHub이 Electron 창(embedded UA) 로그인을 경고·차단할 수 있음(정책 미확인) | 1단계 인증 불가 → 2단계 선행 | I1 스파이크에서 실측 |
| R2 | Notion OAuth 302 체인 도메인이 허용 목록과 다를 수 있음 | 연결 실패 | I1에서 실제 도메인 기록 |
| R3 | 운영 API 오리진이 저장소에 없음(`API_BASE_URL_PROD`) | prod 빌드 허용 목록 | 팀에 값 확인, `desktop/src/shared/env.ts`에 고정 |
| R4 | access 1시간·리프레시 없음 | 1단계 UX 저하 | 2단계(I9)로 해소. 그전까지 재로그인 안내 |
| R5 | macOS 서명·공증 계정 부재 | 알림·safeStorage·쿠키 암호화·자동 업데이트 불가. Sequoia부터 미공증 앱은 시스템 설정에서 수동 승인 필요 | Apple Developer Program(US$99/년) 개설 결정, 소유 주체·인증서 보관 |
| R6 | Windows 서명: Azure Artifact Signing은 조직 계정만(개인 개발자는 미국·캐나다만, 한국 조직 자격은 문서 불일치), Basic US$9.99/월. EV는 SmartScreen 이점 없음 | 미서명 시 "Windows protected your PC" 경고, 기관 관리 PC·Smart App Control은 차단 가능 | 조직 계정 가능 여부 확인 후 미서명 출시 + 안내 vs Artifact Signing 결정 |
| R7 | 크래시 수집 서버·개인정보 | 운영 가시성 | Sentry vs 자체 vs 미수집 결정, 고지 문구 |
| R8 | Cloudflare `_headers` CSP가 Emotion 인라인 스타일과 충돌 | 웹 스타일 깨짐 | I5에서 `style-src` 정책 실험 |
| R9 | Electron 8주 메이저 주기 | 유지보수 부담 | Renovate + EOL 알림, 분기 1회 업그레이드 |
| R10 | `desktop/` 추가로 Governance area 확장 요구 | 규칙 변경 | `fe`로 시작, 필요 시 conventions 변경 Issue |
| R11 | 단일 NCP 인스턴스 전제(인메모리 스트림 레지스트리·인가 코드) | 수평 확장 시 재설계 | 현행과 동일 제약, ADR 212·328 |
| R12 | Anthropic 모델 교체 시 품질·TTFT 회귀 | 답변 품질 | gold set 30문항·TTFT 실측 통과 전 미전환 |
| R13 | Tauri·PWA 재검토 조건 | 셸 교체 | 배포 크기 최우선·Rust 인력·WebKit QA·모바일 계획이 동시에 생길 때만(Tauri), 데스크톱 고유 기능 요구가 사라질 때(PWA) |

## 16. 참고

- 검토 문서: [`llm-electron-subscription-architecture-review.md`](./llm-electron-subscription-architecture-review.md)
- 지식 문서: [`electron-desktop-app-knowledge.md`](./electron-desktop-app-knowledge.md) — 출처 URL·확인 날짜·미확인 항목 전체
- Electron Security: https://www.electronjs.org/docs/latest/tutorial/security
- Electron Fuses: https://www.electronjs.org/docs/latest/tutorial/fuses
- RFC 8252 (OAuth 2.0 for Native Apps): https://www.rfc-editor.org/rfc/rfc8252
- RFC 9700 (OAuth 2.0 Security BCP): https://www.rfc-editor.org/rfc/rfc9700
- Spring Security Resource Server JWT: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- Agent SDK 정책 조항: https://code.claude.com/docs/en/agent-sdk
- Knot 공통 Issue 계약: [`harness/issue-planning.md`](./harness/issue-planning.md), ADR 규칙: [`adr/README.md`](./adr/README.md)
