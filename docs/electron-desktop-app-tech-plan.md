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

> Knot 데스크톱 앱은 `https://knoted.kr` 웹 앱을 원격 로드하는 Electron 셸이다. 탐색(채팅)은 서버가 검색·저장을 맡고 데스크톱 main이 사용자 LLM(로컬 모델 또는 본인 API 키)을 호출한다(2026-09-07 개정, 6.4). 데스크톱이 더하는 것은 상시 실행, 딥링크, 알림, 퀵 질문 창, 자동 업데이트, 그리고 이 탐색 경로다. 인증은 웹·데스크톱 모두 `Authorization: Bearer <JWT>`를 쓰고 쿠키를 쓰지 않는다. 토큰은 웹이 `localStorage`, 데스크톱이 main의 `safeStorage`에 둔다(`D11`, 5.1). 2단계에서 시스템 브라우저 로그인 + 리프레시 토큰(디바이스 세션)을 얹는다. 백엔드 `LlmClient`의 Anthropic 어댑터(B안)는 브라우저 단독 실행 경로(로드맵 Q22)에만 쓰인다.

제안 대비 무엇이 달라졌는지:

| 제안(검토 대상) | 본 기획 | 이유 |
| --- | --- | --- |
| Electron Main이 Agent SDK로 사용자 구독으로 모델 호출 | 데스크톱 main이 **사용자 본인 API 키 또는 로컬 모델**로 호출(6.4, 2026-09-07 개정). 구독 OAuth·세션 토큰 중개는 계속 제외 | 서드파티 제품의 claude.ai 로그인·구독 한도 제공은 사전 승인 없이 금지(지식 §6.1). 본인 키·로컬 모델은 허용 경로 |
| in-process MCP `search_knowledge` 툴 + `POST /v1/search` | 서버가 검색 API로 청크 8개와 규칙 문장을 돌려주고 데스크톱 main이 system prompt에 주입(6.4, 2026-09-07 개정). 툴 루프는 두지 않는다 | 검색 REST·클라이언트 저장 API·무결성 정책이 필요해졌다(로드맵 Q24·Q25). 규칙 문장·선별·게이트·저장 검증은 서버가 소유해 정책 분기를 막는다 |
| SDK `resume`로 세션 관리 | `chat_sessions`·`chat_messages` DB가 세션 진실 | 다기기·재설치 복원 요구(기능 기획서 12절) |
| 서비스 JWT Bearer 가정 | 1단계부터 Bearer JWT로 전환(`D11`), 2단계에서 디바이스 세션·리프레시 추가 | 현행 필터가 쿠키만 읽는 것(지식 §1.2)을 `D11`에서 Bearer만 읽도록 바꾼다 |
| 로컬 번들 또는 원격 URL | 원격 URL 고정 | 로컬 번들은 `__Host-`·SameSite=Lax·CORS에서 구조적으로 깨짐(지식 §2.4, §4.7) |

## 2. 목표와 비목표

### 2.1 목표

| ID | 목표 | 완료 판정 |
| --- | --- | --- |
| G1 | 데스크톱 앱에서 웹과 같은 기능(GitHub 로그인, 워크스페이스, 초대, Notion 연결·동기화, 채팅, 출처)을 쓸 수 있다 | 웹 E2E 시나리오를 Electron에서 통과 |
| G2 | 탐색 계약(6.4: 검색·저장·출처 API, 문서 준비 게이트, 세션당 진행 중 턴 1개, Workspace JOIN 검증, 규칙 문장)을 서버가 소유하고, 웹이 받는 이벤트 모양(`chunk`/`complete`/`error`)은 전송 경로와 무관하게 같다 | 계약 테스트 통과, 브라우저 단독 SSE 경로 회귀 없음(2026-09-07 개정) |
| G3 | macOS(arm64·x64)와 Windows(x64)에 서명된 설치본을 배포하고 자동 업데이트한다 | 서명·공증 통과, 업데이트 종단 테스트 |
| G4 | `knot://` 딥링크로 초대 링크와 로그인 콜백을 앱이 받는다 | 패키징된 앱에서 콜드·웜 스타트 모두 동작 |
| G5 | Electron 공식 보안 체크리스트 20항목과 권장 Fuses를 모두 충족한다 | 8절 체크리스트 리뷰 통과 |
| G6 | 웹 사용자에게 회귀가 없다 | 웹 배포 워크플로우·E2E 무변경 통과 |
| G7 | 앱 셸 릴리스 없이 웹 배포만으로 기능 변경이 반영된다 | 셸 릴리스 주기 ≥ 4주, 웹 배포는 현행 유지 |

### 2.2 비목표

- 사용자 개인 Claude 구독으로 모델을 호출하는 것(검토 문서 8절 전제 조건이 모두 충족되기 전까지).
- renderer(웹 SPA)에서 LLM을 직접 호출하는 것, 서버 검증 없이 답변·출처를 저장하는 것(2026-09-07 개정. main의 사용자 LLM 호출은 6.4의 목표다).
- 오프라인 사용. 원격 로드 구조의 한계이며 별도 결정으로 분리한다(지식 §5.2).
- Tauri·PWA로의 전환. 비교는 지식 §5.1에 두고 재검토 조건만 15절에 남긴다.
- 모바일, Linux 코드 서명·스토어 배포(Linux는 빌드만 제공).
- 웹 UI에 없는 데스크톱 전용 탐색 화면. 데스크톱은 전송 경로(preload `chat`)만 다르고 화면은 웹과 같다.

## 3. 핵심 결정 요약

| ID | 결정 | 선택 | 실제로 검토한 대안 | 선택 이유 | ADR |
| --- | --- | --- | --- | --- | --- |
| D1 | 데스크톱 셸 | **Electron 44** | Tauri 2, PWA | 웹과 동일한 Chromium 렌더링, main·preload·renderer 전부 TypeScript, Playwright 지원, 채택 사례(지식 §5.1). PWA는 트레이·글로벌 단축키·자동 시작이 없음 | 필요 |
| D2 | 콘텐츠 로드 | **원격 URL `https://knoted.kr`** | 로컬 번들(`app://`) | CORS·라우터·SSE를 웹과 동일하게 유지, 웹 배포로 즉시 반영(Slack 하이브리드 모델). 로컬 번들을 막던 쿠키 제약(`__Host-`·SameSite)은 `D11`로 사라졌으므로 전환 검토(`A12`)는 열려 있다(지식 §2.4, §4.7) | D1과 함께 |
| D3 | 모델 호출 위치 | **데스크톱 main이 사용자 LLM(본인 키·로컬 모델) 호출, 서버는 검색·저장(6.4, 2026-09-07 개정)**. 브라우저 단독 경로는 B안 유지(로드맵 Q22) | B안(백엔드 어댑터, 개정 전 선택), C안(Workspace BYO 키), D안(사용자 구독 Agent SDK) | 사용자 지시(2026-09-07). 구독 OAuth를 빼면 정책 허용 경로다. 검색·게이트·규칙 문장·저장 검증을 서버에 남겨 검토 문서 5.4의 무결성·정책 분기 문제를 좁힌다 | 필요(로드맵 `S1`) |
| D4 | 인증 | **1단계: 앱 창 GitHub 로그인 + Bearer JWT(`D11`) → 2단계: 시스템 브라우저 + 디바이스 토큰(패턴 A·B), Device flow(D)는 fallback** | 처음부터 2단계, 1단계에서 멈추기 | 1단계는 로그인 창 위치만 앱 안이고 자격증명 전달은 이미 2단계와 같은 Bearer다. RFC 8252 §8.12·1시간 만료·리프레시 부재가 상시 실행 앱에 부적합하므로(지식 §4.4) 2단계는 유지하되, 남은 차이는 **로그인 창 위치와 리프레시**뿐이다 | 필요(ADR 314 재논의) |
| D11 | 인증 자격증명 전달·저장 | **`Authorization: Bearer <JWT>` + 클라이언트 저장(웹 `localStorage`, 데스크톱 main `safeStorage`). 쿠키·CSRF 폐기** | 현행 `HttpOnly` 쿠키 유지, 쿠키+Bearer 이중 경로, 메모리 전용 저장 | 쿠키는 저장 위치를 브라우저가 정해 데스크톱이 `safeStorage`를 쓸 수 없고, `app://` 로컬 번들에서 깨진다(지식 §2.4·§4.7). 이중 경로는 필터·CSRF 매처·CORS가 두 경로를 동시에 지탱해야 한다. 메모리 전용은 새로고침마다 재로그인이라 리프레시 토큰(2단계) 없이는 못 쓴다. 대가로 `HttpOnly`의 XSS 격리를 잃는다(5.1·5.3) | 필요(ADR 314 보완) |
| D5 | 빌드·패키징 도구 | **Electron Forge 7.x** | electron-builder 26, electron-vite | Electron 공식 권장, Fuses·ASAR 무결성·서명·공증·publisher 통합, Squirrel + update.electronjs.org 무료 경로. electron-builder는 NSIS·차등 업데이트·스테이지 롤아웃·프라이빗 업데이트가 필요할 때 유리(지식 §3.2). 상세는 10절 | D1과 함께 |
| D6 | 자동 업데이트 채널 | **GitHub Releases + `update-electron-app`(update.electronjs.org)** | electron-updater + generic 서버, 자체 서버(Hazel 등) | 저장소가 공개(PUBLIC)라 무료 서비스 조건(공개 저장소 + macOS 서명) 충족. 자체 서버는 2년 이상 정체. 상세는 10절 | D5와 함께 |
| D7 | 저장소 위치 | **루트 `desktop/` 독립 pnpm 패키지, 브랜치 area `fe`** | `frontend/desktop/` 하위 패키지 | `deploy-frontend-*.yml`이 `frontend/**`를 감시하므로 분리해야 웹 배포가 불필요하게 돌지 않음. Governance area는 `be|fe`뿐이라 `fe`를 쓴다(지식 §1.6) | 불필요(메모) |
| D8 | 세션 진실 | **DB(`chat_sessions`·`chat_messages`)** | SDK 로컬 JSONL | 다기기 복원·서버 계측 요구 | 불필요(현행) |
| D9 | 프롬프트 정책·게이트·계측 | **규칙 문장·게이트·저장 검증은 서버, 프롬프트 조립·TTFT 계측은 main(6.4)** | 앱 내 systemPrompt 전체, 서버 전체 고정(개정 전) | 규칙 문장을 서버가 돌려주므로 앱 버전별 정책 분기는 없다. 계측은 `S5`에서 저장 API로 서버에 전달한다(2026-09-07 개정) | D3과 함께 |
| D10 | 관측 | **`electron-log` 파일 로그 + Electron `crashReporter`(수집 서버는 미결)** | Sentry Electron SDK | 개인정보·비용 결정이 필요해 15절 미결로 둠 | 필요 시 |

## 4. 시스템 아키텍처

### 4.1 구성도

```text
┌──────────────────────── 사용자 PC ────────────────────────┐
│ Electron 앱 (desktop/)                                     │
│ ┌ main (Node) ───────────────────────────────────────────┐ │
│ │ 창 관리 · 메뉴 · 트레이 · 딥링크 수신 · 업데이트 ·      │ │
│ │ 네비게이션 허용 목록 · 권한 핸들러 · 로그/크래시 ·      │ │
│ │ 액세스 토큰 보관(safeStorage, 1단계) ·                   │ │
│ │ 탐색(6.4): 검색 API 호출 · 프롬프트 조립 ·               │ │
│ │   사용자 LLM 스트리밍 호출 · chunk 중계 · 저장 API 호출 ·│ │
│ │   LLM 키 보관(safeStorage) ·                             │ │
│ │ [2단계] 리프레시·갱신 + onBeforeSendHeaders Bearer 주입  │ │
│ └──────────┬───────────────── contextBridge/IPC ──────────┘ │
│ ┌ preload ─┴──────────────┐  ┌ renderer (sandbox) ────────┐ │
│ │ window.knotDesktop 노출 │  │ https://knoted.kr SPA      │ │
│ │ (auth · chat · llm,     │  │ React 19 · axios ·         │ │
│ │  sender 검증)           │  │ 탐색은 preload chat 경유   │ │
│ └─────────────────────────┘  └──────────────┬─────────────┘ │
└──────────┬────────────────────────────────────┼─────────────┘
           │ HTTPS · 사용자 키/엔드포인트          │ HTTPS (웹과 동일, Bearer)
   ┌───────▼────────┐   ┌──────────────────┐  ┌──▼─────────────────────────┐
   │ 사용자 LLM      │   │ Cloudflare Workers│  │ Spring Boot 4.1 (api.*)     │
   │ 로컬 모델 또는  │   │ 정적 자산 · SPA   │  │ auth · workspace · chat ·   │
   │ 본인 API 키     │   │ fallback          │  │ search · notion import      │
   └────────────────┘   └──────────────────┘  │ 검색 API(청크 8) · 저장 API │
                                              │ LlmClient(브라우저 단독만)  │
                                              └──────┬──────────────────────┘
                                                     │
                                              ┌──────▼─────┐
                                              │ PostgreSQL │
                                              │ + pgvector │
                                              └────────────┘
```

main도 같은 Bearer 토큰으로 Spring의 검색·저장 API를 부른다(그림에서는 renderer 화살표에 합쳤다). 사용자 LLM 호출은 main에서만 나가며 renderer·서버는 LLM 키를 모른다(6.4, 2026-09-07 개정).

### 4.2 프로세스별 책임

| 프로세스 | 책임 | 하지 않는 것 |
| --- | --- | --- |
| main | `BrowserWindow` 생성·복원, 애플리케이션 메뉴, 트레이, 단일 인스턴스 락, 딥링크 파싱, `will-navigate`·`setWindowOpenHandler`·`setPermissionRequestHandler`, 자동 업데이트, 로그·크래시, **액세스 토큰 보관(`safeStorage`, 1단계)**, **탐색(6.4): 서버 검색 API 호출·프롬프트 조립·사용자 LLM 스트리밍 호출·`chunk` 중계·서버 저장 API 호출·LLM 키 보관(`safeStorage`)**, [2단계] 리프레시·갱신·`onBeforeSendHeaders` Bearer 주입 | 검색·선별·저장 자체(서버가 한다), 쿠키 값 읽기(`cookies.get`을 쓰지 않는다), 토큰·LLM 키·프롬프트 본문 로깅, renderer에 LLM 키 노출 |
| preload | `contextBridge.exposeInMainWorld('knotDesktop', …)`로 4.4의 API만 노출(`auth`·`chat`·`llm` 포함). CJS 단일 번들, sandbox 유지 | `ipcRenderer` 원본 노출, Node API 노출 |
| renderer | 웹 SPA 그대로. `window.knotDesktop` 존재 여부로 데스크톱을 감지해 외부 링크·딥링크·로그아웃 UX와 **토큰 저장소**, **탐색 전송 경로**(`knotDesktop.chat`이 있으면 IPC, 없으면 SSE)만 분기 | Electron 모듈 직접 접근, 토큰을 `localStorage`에 두는 것(데스크톱에서는 preload 경유), LLM 직접 호출·LLM 키 보관 |
| 백엔드 | 현행 전부 + **Bearer 인증(`D11`)** + **탐색 검색 API·답변 저장 API·출처 8건(6.4)** + [B안] Anthropic 어댑터(브라우저 단독 경로) + [2단계] 디바이스 토큰 API | 데스크톱 경로의 LLM 호출, 인증 쿠키 발급 |

### 4.3 주요 흐름

**탐색(데스크톱, 2026-09-07 개정 — 6.4)**: renderer의 `streamChatMessageApi`가 `window.knotDesktop.chat`이 있으면 `chat.ask({sessionId, content})`로 IPC → main이 `POST /api/v1/conversations/{id}/search`를 `Authorization: Bearer <JWT>`로 호출 → 서버가 검사·USER 저장·하이브리드 검색 후 청크 8개와 규칙 문장을 응답(READY가 아니면 안내 문구를 저장하고 응답) → main이 system(규칙 + 청크 8개)·messages(세션 이력)를 조립해 사용자 LLM에 스트리밍 요청 → delta마다 renderer에 `chunk` 이벤트 → 끝나면 `POST …/messages/assistant`로 답변·근거 저장 → `complete(messageId)` → renderer가 `GET /messages/{id}/sources`(8건, 페이지로 묶어 표시). renderer가 받는 이벤트 모양은 SSE와 같다.

**탐색(브라우저 단독, 로드맵 Q22)**: renderer의 `streamChatMessageApi`가 `POST /api/v1/conversations/{sessionId}/messages`를 `Authorization: Bearer <JWT>`로 호출 → 백엔드 파이프라인(게이트 → 검색(청크 8) → `LlmClient` → SSE → 저장) → `complete(messageId)` → `GET /messages/{id}/sources`. Electron renderer는 Chromium이므로 fetch 스트리밍·`TextDecoder`·`parseSseEvents`가 브라우저와 동일하게 동작한다(지식 §2.5).

**로그인 1단계(`D11`)**: SPA의 `GithubLoginButton`이 `window.location.href = {API}/oauth2/authorization/github` → main의 `will-navigate`·`will-redirect` 허용 목록(웹 오리진, API 오리진, `github.com`)을 통과 → GitHub 로그인 → `api.*`의 성공 핸들러가 **쿠키를 심지 않고** 설정된 프론트 URL에 토큰을 URL 프래그먼트로 붙여 302(`{success-redirect-uri}#access_token=…&expires_in=3600`, 신규 가입은 `{nickname-redirect-uri}#onboarding_token=…`) → SPA 부팅 코드가 프래그먼트를 읽어 저장소에 넣고 `history.replaceState`로 주소창에서 지운다 → `EntryRedirect`가 분기. 세션 파티션은 `persist:knot`을 유지하지만 이제 인증 쿠키는 담기지 않는다.

프래그먼트를 쓰는 이유: `#` 뒤는 서버로 전송되지 않아 액세스 로그·`Referer`에 남지 않는다. 쿼리스트링(`?access_token=`)은 Cloudflare·백엔드 로그와 `Referer`에 그대로 찍힌다. 남는 노출은 브라우저 히스토리 한 곳뿐이고 SPA가 즉시 지운다(로드맵 Q14).

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
  // 1단계(D11): SPA가 액세스 토큰을 둘 곳. 웹에서는 이 객체가 없고 localStorage를 쓴다
  auth?: {
    getToken(): Promise<string | null>;     // 없으면 null
    setToken(token: string): Promise<void>; // safeStorage로 암호화해 userData에 저장
    clearToken(): Promise<void>;            // 로그아웃·401
    // 2단계(A7)에서 추가한다
    startLogin?(): Promise<void>;           // 시스템 브라우저를 연다
    logout?(): Promise<void>;               // 폐기 API 호출 + 로컬 삭제
    onSessionChanged?(handler: (state: 'signed-in' | 'signed-out') => void): () => void;
  };
  // 탐색(6.4, 2026-09-07). 이벤트 모양은 웹 SSE의 ChatStreamEvent와 동일
  chat?: {
    ask(
      input: { sessionId: number; content: string },
      onEvent: (event: ChatStreamEvent) => void,
    ): Promise<{ cancel(): void }>;
  };
  // 사용자 LLM 설정. 키 값은 돌려주지 않는다(hasApiKey만)
  llm?: {
    getSettings(): Promise<UserLlmSettings>;
    setSettings(input: UserLlmSettingsInput): Promise<void>; // apiKey는 safeStorage
    clearApiKey(): Promise<void>;
  };
  notifications?: { show(input: { title: string; body: string; link?: KnotDeepLink }): Promise<void> };
}

export type ChatStreamEvent =
  | { event: 'chunk'; data: { delta: string } }
  | { event: 'complete'; data: { messageId: number } }
  | { event: 'error'; data: { code: string; message: string } };
export interface UserLlmSettings {
  provider: 'openai-compatible' | 'anthropic';
  baseUrl: string;   // 예: http://localhost:1234/v1, https://api.anthropic.com
  model: string;
  hasApiKey: boolean;
}
export type UserLlmSettingsInput = Omit<UserLlmSettings, 'hasApiKey'> & { apiKey?: string };
```

규칙:

- preload는 `ipcRenderer.invoke`/`ipcRenderer.on`을 래핑한 함수만 노출한다. `event` 객체를 renderer 콜백에 넘기지 않는다.
- main의 모든 `ipcMain.handle`은 `event.senderFrame`의 origin이 허용된 웹 오리진일 때만 처리한다(`senderFrame`이 `null`이면 거부).
- `auth.callback` 같은 로그인 콜백 데이터는 main에서만 소비하고 renderer로 보내지 않는다.
- `auth.getToken`/`setToken`/`clearToken`은 **저장소만** 노출한다. 값은 renderer가 들고 있다가 `Authorization` 헤더에 직접 넣는다. 2단계에서 main이 `onBeforeSendHeaders`로 주입하게 되면 `getToken`은 `null`을 돌려주도록 바꾸고 SPA는 헤더를 붙이지 않는다(옵셔널 접근이라 하위 호환이 유지된다).
- 토큰 값은 로그·크래시 리포트에 절대 쓰지 않는다. IPC 인자 로깅도 금지한다.
- `chat.ask`는 IPC 채널 `knot:chat-ask`(invoke → requestId)·`knot:chat-cancel`·`knot:chat-event`(main → renderer)로 구현한다. main은 세션당 진행 중 요청을 하나만 두고, 첫 조각까지 30초(로드맵 Q26)를 넘기면 `LLM_STREAM_TIMEOUT`을 보낸다. `event` 객체는 renderer에 넘기지 않는다(2026-09-07, 6.4).
- `llm.getSettings`는 키 값을 돌려주지 않는다(`hasApiKey`만). `setSettings`의 `apiKey`는 main이 `safeStorage`로 암호화해 `userData/llm-key.bin`에 두고, 나머지 설정은 `userData/llm-settings.json`에 둔다. 키·프롬프트 본문은 로그·IPC 인자 로깅에 쓰지 않는다.
- API 추가는 이 인터페이스 파일의 변경으로만 하며, 웹 SPA는 `window.knotDesktop?.xxx` 옵셔널 접근으로 하위 호환을 지킨다(셸 업데이트가 웹 배포보다 느리다).

### 4.5 오리진·환경 정책

| 환경 | 웹 오리진(로드 URL) | API 오리진 | 비고 |
| --- | --- | --- | --- |
| prod | `https://knoted.kr` | GitHub vars `API_BASE_URL_PROD` 값(저장소에 없음). 빌드 환경변수로 주입한다(Q3) | 서명 빌드 |
| dev | `https://dev.knoted.kr` | `https://dev-api.knoted.kr` (정정 2026-09-06, `A1` 실측) | 내부 테스트 빌드 |
| local | `http://localhost:3000` | `http://localhost:8080` (정정 2026-09-07) | 프론트 `pnpm dev`(`API_MOCKING=false`) + 백엔드 `bootRun`과 함께 |

- 환경은 빌드 시 상수로 고정한다(`KNOT_DESKTOP_ENV`). 런타임 전환 UI는 두지 않는다(피싱 표면).
- **`local`은 실 백엔드를 본다**(정정 2026-09-07). 처음에는 `API_MOCKING=true`인 mock 프론트 단독 구동을 전제해 웹과 API를 같은 오리진(`:3000`)으로 뒀다. 그 조합으로는 셸 안에서 실제 GitHub OAuth가 한 번도 지나가지 않는다 — devServer의 302 미들웨어가 로그인을 대신하므로 백엔드로 나가는 홉 자체가 없고, 따라서 허용 목록도 검증되지 않는다. 실 로그인은 SPA가 `http://localhost:8080/oauth2/authorization/github`로 이동하면서 시작하므로 이 오리진이 목록에 있어야 한다. 없으면 `will-navigate`에서 차단된 뒤 대체 경로인 `shell.openExternal`마저 `http:`라 거부해 **로그인 버튼이 무반응**이 된다(2026-09-07 실측). mock 구동을 막지는 않는다 — 그때는 `:8080` 홉이 없어 목록에 있어도 지나가지 않는다.
- **API 오리진을 추정하지 않는다**(정정 2026-09-06). dev의 실제 값이 `dev-api.knoted.kr`로 확인되면서 `api.<env>.knoted.kr` 대칭 가정이 깨졌다. prod 값은 `A3` 배포 시점에 사람이 `KNOT_API_ORIGIN`으로 주입한다.
- 네비게이션 허용 목록: 웹 오리진, API 오리진, `https://github.com`(1단계 로그인), GitHub 소셜 로그인 IdP(`https://accounts.google.com` — 추가 2026-09-07), Notion OAuth 경유 도메인(`https://api.notion.com`, `https://app.notion.com` — 추가 2026-09-08 `A1` 실측, `https://www.notion.so` — 미관측이나 로그인 홉 가능성으로 유지). 목록 밖 URL은 `preventDefault` 후 `shell.openExternal`.
- **GitHub 로그인 폼이 제공하는 소셜 로그인(Google·Apple) 경유 도메인도 목록에 있어야 한다**(추가 2026-09-07, `A1` 실측). GitHub 계정 자체를 Google로 만든 사용자는 `github.com/login`에서 "Sign in with Google"을 누르고, 체인이 `github.com/sessions/social/google/initiate` → `accounts.google.com/o/oauth2/v2/auth`로 나간다. 이 홉을 외부 브라우저로 넘기면 Google 인증만 다른 브라우저에서 끝나고, GitHub이 소셜 로그인 `state`를 심어둔 세션 쿠키는 앱 세션에 남아 있으므로 콜백에서 대조가 실패한다(관측 문구: "We could not validate the response from your social login provider"). **로그인 체인은 한 브라우저 세션 안에서 끝나야 한다** — 중간 홉만 외부로 빼면 흐름이 깨진다.
- Apple(`https://appleid.apple.com`)은 같은 이유로 아직 깨진다. 필요해지면 같은 근거로 추가한다(미해소, 2026-09-07).
- **Notion OAuth의 동의 화면은 `app.notion.com`에 있다**(추가 2026-09-08, `A1` 실측, 로드맵 U2). SPA가 `https://api.notion.com/v1/oauth/authorize?…`로 이동하면 Notion이 302로 `https://app.notion.com/install-integration?…`에 보낸다. 이 오리진이 목록에 없으면 `will-redirect`에서 차단돼 외부 브라우저로 빠지고, 동의를 거기서 마치면 백엔드 연결은 성공하지만 결과 화면(`?result=connected`)도 외부 브라우저로 돌아가며, 앱 창의 SPA는 이동 대기 상태에 갇혀 **연결 버튼이 무한 로딩**이 된다(R21). 동의 이후 홉(Notion 로그인·콜백 복귀)은 아직 앱 창 안에서 실측되지 않았다 — 새 도메인이 나오면 같은 근거로 추가한다.
- **차단은 `will-navigate`와 `will-redirect` 두 이벤트 모두에 건다**(정정 2026-09-06, `A1`). `will-navigate`는 링크 클릭·`window.location` 변경 같은 네비게이션 *시작*에서 발화하고, 그 네비게이션 도중의 서버 302는 `will-redirect`로 발화한다. OAuth 로그인은 302 체인이므로 `will-navigate`만 막으면 허용 오리진에서 시작한 뒤 목록 밖으로 넘어가는 경로가 통과한다. `did-start-navigation`·`did-redirect-navigation`은 취소할 수 없어 **기록 전용**으로 쓴다(U2 체인 수집).
- 새 창(`window.open`, `target=_blank`)은 전부 `deny` + 검증된 `https:`만 외부 브라우저.
- CORS: 원격 로드이므로 백엔드 `AUTH_CORS_ALLOWED_ORIGINS` 변경이 없다. 2단계 Bearer 요청도 renderer 오리진이 웹 오리진이라 동일하다.

## 5. 인증 설계

### 5.1 1단계: 웹·데스크톱 공통 Bearer JWT (`D11`, 2026-09-06 개정)

**요지**: 인증 자격증명을 `__Host-KNOT_ACCESS_TOKEN` 쿠키에서 `Authorization: Bearer <JWT>` 헤더로 옮긴다. 웹과 데스크톱이 같은 경로를 쓰고, 인증 쿠키와 CSRF는 폐기한다. 이전 판의 "웹 쿠키 세션을 데스크톱이 그대로 재사용한다"는 설계는 여기서 폐기된다.

**왜 바꾸나**

| 이유 | 내용 |
| --- | --- |
| 저장 위치를 클라이언트가 고를 수 있다 | 쿠키는 저장 위치를 브라우저가 정하므로 데스크톱이 `safeStorage`(macOS Keychain·Windows DPAPI)에 넣을 수 없었다. Bearer는 웹이 `localStorage`, 데스크톱이 main의 암호화 파일에 각각 둘 수 있다 |
| 2단계와 전송 방식이 같아진다 | 2단계 디바이스 토큰도 Bearer다. 1단계를 쿠키로 두면 필터·CSRF 매처·CORS가 두 경로를 동시에 지탱해야 한다. 이제 2단계와의 차이는 **로그인 창 위치와 리프레시**뿐이다 |
| 로컬 번들(`app://`) 전환이 열린다 | `__Host-`·SameSite=Lax는 `app://` 오리진에서 구조적으로 깨진다(지식 §2.4, §4.7). Bearer는 오리진에 의존하지 않으므로 `A12`의 선행 조건이 사라진다 |
| CSRF가 통째로 필요 없어진다 | CSRF는 브라우저가 자동으로 붙이는 자격증명(쿠키)을 노린 공격을 막는 장치다. 자동 첨부가 사라지면 토큰·쿠키·403 재시도 경로가 전부 불필요하다 |

**대가**: `HttpOnly`가 주던 XSS 격리를 잃는다. 웹 번들이 토큰을 읽을 수 있으므로 XSS 하나가 곧 토큰 유출이다. 이를 받는 대신 CSP(9.4)·1시간 만료·리프레시 부재를 유지하고, 2단계에서 데스크톱 토큰을 renderer 밖으로 옮긴다. 이 교환은 로드맵 `Q14`에 결정으로 남긴다.

**계약**

| 항목 | 내용 |
| --- | --- |
| 전송 | `Authorization: Bearer <JWT>`. 인증이 필요한 모든 요청(axios·SSE fetch 공통) |
| 토큰 | 현행 access JWT 그대로. HS256, `token_type=ACCESS`, issuer `https://knoted.kr`, audience `knot-api`, 만료 1시간. **발급 로직·클레임은 바꾸지 않는다** |
| 로그인 전달 | 성공 핸들러가 쿠키 대신 리다이렉트 URL의 **프래그먼트**에 실어 보낸다. 기존 회원 `{success-redirect-uri}#access_token=<jwt>&token_type=Bearer&expires_in=3600`, 신규 가입 `{nickname-redirect-uri}#onboarding_token=<jwt>&expires_in=600` |
| 온보딩 | `POST /api/v1/auth/nickname`이 온보딩 토큰을 `Authorization: Bearer`로 받고, 응답 본문 `200 {accessToken, tokenType, expiresIn}`으로 액세스 토큰을 돌려준다(기존 204 + 쿠키에서 변경) |
| 저장(웹) | 액세스 토큰은 `localStorage`의 `knot.accessToken`. 탭·새로고침·재시작을 넘겨 로그인이 유지되던 기존 쿠키 동작과 같게 맞춘다. 온보딩 토큰은 슬롯을 나눠 `sessionStorage`의 `knot.onboardingToken`에 둔다(로드맵 Q19) |
| 저장(데스크톱) | preload `window.knotDesktop.auth`(4.4) → main `safeStorage.encryptString` → `userData/auth.bin`. `isEncryptionAvailable()`이 false면 저장하지 않고 메모리로만 들고 있다가 앱 종료 시 잃는다(Linux `basic_text`) |
| 로그아웃 | 클라이언트가 저장소에서 지우는 것이 실제 로그아웃이다. `POST /api/v1/auth/logout`은 204만 돌려주는 훅으로 남긴다(2단계에서 서버 폐기가 붙을 자리) |
| 만료 | access 1시간, 리프레시 없음. 401을 받으면 클라이언트가 토큰을 지우고 `AuthGuard`가 `/login`으로 보낸다 |
| 회원 확인 | 서명·만료·issuer·audience·`token_type` 검사를 통과한 뒤 subject의 회원이 `members`에 있는지 **요청마다** 확인한다(PK 조회 1회). 없으면(탈퇴·DB 초기화) 인증하지 않아 401 `UNAUTHENTICATED`가 되고, 클라이언트는 만료와 같은 경로로 토큰을 지운다(로드맵 Q41). 발급 로직·클레임은 그대로다 |
| CSRF | 폐기. `CookieCsrfTokenRepository`·`GET /api/v1/auth/csrf`·`X-XSRF-TOKEN`을 제거한다 |
| CORS | 허용 헤더에 `Authorization` 추가, `X-XSRF-TOKEN` 제거, `allowCredentials=false`(더 이상 쿠키를 싣지 않는다) |
| Fuse | `EnableCookieEncryption`은 그대로 켠다. 인증 쿠키는 없어졌지만 남는 쿠키(OAuth 세션 등)의 디스크 암호화에 여전히 유효하다 |

**호환 기간을 두지 않는다.** 쿠키 경로와 Bearer 경로를 동시에 지탱하면 필터가 두 자격증명을 받아들이게 되고, 그 상태에서는 CSRF도 켜 둔 채로 남겨야 한다. 프론트·백엔드를 같은 시점에 배포하고, 배포 순간 살아 있던 세션은 만료된다(재로그인 1회). 배포 순서는 백엔드 먼저이며, 그 사이 구버전 SPA는 401을 받아 `/login`으로 간다(로드맵 `Q17`).

**남는 위험(쿠키 시절과 동일)**: (1) RFC 8252 §8.12 — GitHub 로그인 페이지를 앱 창(embedded user-agent)에 띄운다. GitHub의 차단 정책은 미확인이고 Google은 차단한다(지식 §4.3). (2) 앱 창은 브라우저의 GitHub 세션을 공유하지 못해 매번 로그인한다. (3) WebAuthn(패스키) 로그인이 제한될 수 있다. 이 셋은 2단계에서 로그인 창을 시스템 브라우저로 옮겨야 해소된다.

### 5.2 2단계: 시스템 브라우저 로그인 + 디바이스 토큰

목표: 앱 안에서 GitHub 자격증명을 입력하지 않고, 1시간마다 재로그인하지 않으며, 기기별로 세션을 관리·폐기할 수 있게 한다. `D11` 이후 1단계도 Bearer이므로 **2단계가 더하는 것은 로그인 창 위치(시스템 브라우저)·리프레시 토큰·기기 세션 폐기 세 가지뿐**이다.

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
  │                               │      성공 핸들러: client=desktop → 프래그먼트 전달 대신       │
  │                               │      일회용 device_code 발급(TTL 120s, S256 challenge 바인딩)  │
  │◀──── 302 http://127.0.0.1:P/callback?code=dc&state=s ◀───────│                                │
  │  (fallback: knot://auth/callback?code=dc&state=s)            │                                │
  │ state 검증, 포트 닫기         │  "앱으로 돌아가세요" 페이지    │                                │
  │─ POST /api/v1/auth/device/token {code: dc, code_verifier: v, device: {name, platform}} ──────▶│
  │◀─ {access_token(JWT 1h), refresh_token(opaque), expires_in} ─│                                │
  │ refresh·access 모두 safeStorage 암호화 후 userData 파일(1단계와 같은 저장소) │                  │
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
| GET | `/api/v1/auth/sessions` | Bearer | 기기 목록 `{id, deviceName, platform, createdAt, lastUsedAt, current}` | — |
| DELETE | `/api/v1/auth/sessions/{id}` | Bearer | 204 | CORS 허용 메서드에 DELETE가 없으므로 웹에서 쓰려면 `SecurityConfig` CORS 메서드 추가 필요 |

**토큰 규격**

- access: 기존 HS256 키·issuer·audience 재사용, `typ=DEVICE_ACCESS`, `sid=<device_session_id>`, 만료 1시간(`auth.jwt.expiration`). 기존 `JwtProvider`의 타입 열거(`ACCESS`, `ONBOARDING`)에 추가.
- refresh: 256-bit 난수 opaque. DB에는 해시만(ADR 254의 HMAC·AES-GCM 관행 재사용), 6개월 미사용 시 만료, rotation + family 폐기(RFC 9700 §4.14.2).
- device_code: 256-bit 난수, TTL 120초, 1회용, `code_challenge`·`state`·`return`과 함께 서버 세션에 보관(`D11` 이후 인증 쿠키가 없으므로 서명 쿠키 대안은 쓰지 않는다).

**데이터**: Flyway `V14__create_device_sessions.sql` — `device_sessions(id, member_id, device_name, platform, app_version, refresh_token_hash, family_id, created_at, last_used_at, expires_at, revoked_at)` + `device_authorization_codes`(또는 인메모리 TTL 캐시, 단일 인스턴스 전제는 ADR 212·328과 동일).

**Spring Security 변경**(지식 §4.6)

- `D11`의 `JwtAuthenticationFilter`(Bearer)에 `typ=DEVICE_ACCESS`·`sid` 유효성(폐기 여부) 검증을 더한다. 자격증명 위치가 이미 `Authorization` 헤더라 필터를 하나 더 두지 않는다.
- CSRF: `D11`에서 이미 제거됐다. 2단계에서 되살릴 이유가 없다(U14 `CsrfFilter.DEFAULT_CSRF_MATCHER` 확인 항목도 함께 사라진다).
- `OAuth2AuthorizationRequestResolver` 커스터마이즈로 `client`·`code_challenge`·`return`을 attributes에 보관, 커스텀 `AuthenticationSuccessHandler`가 `client=desktop` 분기. 이 분기는 ADR 314("백엔드는 고정 리다이렉트 3개")의 재논의 조건("로그인 이후 목적지가 외부에서 지정되는 흐름 추가 시")에 해당하므로 ADR 314를 보완하는 ADR로 기록한다.
- `state`에는 URL이나 민감 정보를 넣지 않고 서버 저장 키만 쓴다(open redirector 방지). `return`은 `loopback:{port}`·`deeplink` 두 값만 허용한다.

**Electron 측**

- loopback을 1차, 딥링크를 2차로 둔다. loopback은 요청 시작 시 임의 포트에 `127.0.0.1`만 바인딩하고 응답 직후 닫는다(RFC 8252 §8.3). 딥링크 스킴은 `knot`(단순) 또는 `kr.knoted.app`(reverse-domain, OAuth 2.1 권고) 중 하나를 D4 ADR에서 정한다.
- 토큰 저장: `safeStorage.encryptString` → `userData/auth.bin`. `isEncryptionAvailable()`이 false(Linux `basic_text`)면 저장하지 않고 매 실행 로그인.
- Bearer 주입: `session.webRequest.onBeforeSendHeaders({urls:[`${API_ORIGIN}/*`]})`에서만. 다른 오리진에는 절대 붙이지 않는다.
- 만료 5분 전 백그라운드 refresh, 실패 시 `signed-out` 이벤트 → SPA가 `/login`으로.
- SPA는 1·2단계 중 어느 쪽인지 알 필요가 없다. 두 단계 모두 `Authorization: Bearer`이고, 데스크톱에서 토큰의 출처만 preload 뒤로 숨는다(4.4).

### 5.3 위협 모델·완화

| 위협 | 완화 |
| --- | --- |
| 딥링크 스킴 탈취(다른 앱이 `knot://` 등록) | 코드 1회용·TTL 120초, S256 verifier 바인딩(코드만으로 교환 불가), 앱 내 state 매칭, loopback 1차 |
| loopback 포트 가로채기 | 동일(PKCE), 응답 즉시 포트 닫기, `127.0.0.1` 전용 바인딩 |
| renderer XSS (`D11`으로 커진 표면) | 1단계에서는 토큰을 renderer가 읽으므로 XSS 하나로 토큰이 유출된다. `HttpOnly` 쿠키가 주던 격리는 없다. 완화: CSP(9.4)·sandbox·contextIsolation·최소 preload API·sender 검증·1시간 만료. 근본 해소는 2단계에서 토큰을 main으로 옮기고 `onBeforeSendHeaders`로 주입하는 것 |
| CSRF | `D11`으로 **소멸**. 브라우저가 자동으로 붙이는 인증 자격증명이 없으므로 교차 사이트 요청은 인증되지 않는다 |
| 토큰이 URL 프래그먼트로 지나감 | 프래그먼트는 서버로 전송되지 않아 로그·`Referer`에 남지 않는다. 브라우저 히스토리에는 남으므로 SPA가 읽는 즉시 `history.replaceState`로 지운다. 확장 경로(일회용 코드 교환)는 2단계 `device_code`와 같은 방식으로 열려 있다 |
| 토큰 파일 탈취 | macOS Keychain·Windows DPAPI(같은 계정의 다른 앱은 복호화 가능 → 위협 모델에 명시), refresh rotation·재사용 감지·기기 목록 폐기 |
| 피싱(가짜 로그인 창) | 앱 안에서 자격증명을 받지 않음(2단계), 환경 전환 UI 없음, 서명된 배포본 |
| 백엔드 리다이렉트 오용 | `return` 값 화이트리스트, `state`에 URL 미포함 |

## 6. 채팅·LLM

### 6.1 데스크톱 관점

2026-09-07 개정. 데스크톱에서는 main이 서버 검색 API로 청크 8개를 받아 사용자 LLM을 호출하고 저장 API로 답변을 돌려보낸다(6.4). `CHAT_DOCUMENTS_NOT_READY` 게이트·세션당 진행 중 턴 1개·`search_references` 저장·규칙 문장은 서버가 유지한다. 브라우저 단독 실행은 기존 SSE 경로를 쓴다(로드맵 Q22). 데스크톱 퀵 질문 창(7절 P2)은 같은 SPA 화면을 로드하므로 자동으로 데스크톱 경로를 탄다.

### 6.2 백엔드 Anthropic 어댑터(B안, 브라우저 단독 경로 — 로드맵 Q22)

| 항목 | 설계 |
| --- | --- |
| 활성화 | `llm.chat.provider=anthropic` 분기를 `LlmClientConfig`에 추가. provider 키 분리와 HTTP 클라이언트 빈 분리는 `B0`에서 끝났다(정정 2026-09-06, 지식 §1.4). 임베딩은 정정 2026-09-08(`B5`): `llm.embedding.provider=gemini`가 실제 임베딩의 기본 선택이며 아래 `임베딩(B5)` 행이 설계다. `openai-compatible`(LM Studio Qwen)·`fake`는 되돌리기용으로 남는다 |
| 임베딩(`B5`) | `search/infrastructure/gemini/GeminiEmbeddingClient implements DocumentEmbeddingClient`가 `POST {llm.gemini.base-uri}/v1beta/models/{llm.gemini.embedding-model}:batchEmbedContents`를 JDK `HttpClient`로 직접 호출한다(헤더 `x-goog-api-key`, 로드맵 Q34). 요청 `requests[]`의 각 항목은 `{model: "models/<모델>", content.parts[].text, taskType, outputDimensionality}`이며 `taskType`은 색인 `RETRIEVAL_DOCUMENT`·질의 `RETRIEVAL_QUERY`(Q36 — `DocumentEmbeddingClient.embed(texts, EmbeddingTask)`로 용도를 넘긴다), `outputDimensionality`는 `llm.embedding.dimensions`(1,024, Q35)다. `gemini-embedding-001`은 3,072 미만을 정규화해 주지 않으므로 어댑터가 응답 `embeddings[].values`를 L2 정규화한 뒤 돌려준다. 설정 키: `llm.gemini.base-uri`(`GEMINI_BASE_URI`, 기본 `https://generativelanguage.googleapis.com`), `llm.gemini.api-key`(`GEMINI_API_KEY`, 필수), `llm.gemini.embedding-model`(`GEMINI_EMBEDDING_MODEL`, 기본 `gemini-embedding-001`), `llm.gemini.request-timeout`(`GEMINI_REQUEST_TIMEOUT`, PT30S). 배치는 `llm.search.embedding-batch-size`(16 — 정정 2026-09-08: 64는 Gemini 429 `RESOURCE_EXHAUSTED`, Q37·U25)다. 오류: 401·403 → `SEARCH_CONFIGURATION_INVALID`, 그 외 비 2xx·건수/차원 불일치·파싱 실패 → `SEARCH_PROVIDER_FAILED`, 키 공백은 기동 실패(Q38). 색인 배치의 429·503은 `llm.gemini.retry-initial-delay`(PT5S)부터 2배씩 `llm.gemini.retry-max-attempts`(6)회 재시도하고 질의는 재시도하지 않는다(Q42, 정정 2026-09-08 — 무료 티어가 분당 약 32청크만 받는 실측). 기존 색인은 자동 변환하지 않고 동기화 재실행으로 재색인한다(Q39). 키·본문은 로그에 남기지 않는다 |
| 설정 | `llm.anthropic.api-key`(`ANTHROPIC_API_KEY`), `llm.anthropic.model`(기본 `claude-opus-5`), `llm.anthropic.effort`(기본 `medium`, 채팅 QA 측정 후 조정), `llm.anthropic.max-tokens`(기본 4096), `llm.anthropic.request-timeout`(PT30S — SSE 타임아웃과 정합), `llm.anthropic.base-uri`(기본 `https://api.anthropic.com`, 테스트·프록시용 — 추가 2026-09-07). `research-loop.enabled`는 `B1`에서 만들지 않는다(로드맵 `B4`로 분리, 정정 2026-09-07) |
| 클래스 | `chat/infrastructure/anthropic/AnthropicLlmClient implements LlmClient`, `AnthropicLlmStream implements LlmStream`(pull형: `hasNext`/`next`가 `content_block_delta.text_delta`만 돌려주고 `message_stop`에서 종료), `AnthropicRequestMapper`(`SearchContext.groundingPrompt` → `system`, 히스토리 → `messages`) |
| HTTP | 옵션 1: `com.anthropic:anthropic-java`(`client.messages().createStreaming`) 도입. 옵션 2: 기존 JDK `HttpClient`로 `POST /v1/messages`(`x-api-key`, `anthropic-version`, `stream:true`) 직접 호출 + SSE 파서 재사용. → **옵션 2로 확정**(로드맵 Q20, 2026-09-07). 의존성 추가 없이 `chat/infrastructure/anthropic/` 안에서 끝나고 기존 어댑터와 구조가 같다 |
| 파라미터 | `thinking` 생략(Opus 5 기본 adaptive. 명시하지 않는 쪽이 모델을 바꿔도 400이 나지 않는다 — 확정 2026-09-07), `output_config.effort`. `temperature`는 넘기지 않는다(Opus 5에서 400). assistant prefill 금지 |
| 캐시 | **`B1`에서는 미적용(정정 2026-09-07)**. Opus 5의 캐시 최소 프리픽스는 512 토큰이고, 그보다 짧은 프리픽스는 `cache_control`이 있어도 조용히 캐시되지 않는다. 고정 부분(`SearchContext.GROUNDING_INSTRUCTION`, 약 330자)이 이를 넘는지 실측 전(로드맵 U18)이라 `B1`은 `system`을 문자열 하나로 보낸다. 넘는 것이 확인되면 `B2`에서 `[근거 규칙(cache_control ephemeral)] + [근거 문서]` 두 블록으로 나눈다 |
| 오류 매핑 | 401/403 → `LLM_CONFIGURATION_INVALID`, 429·529(overloaded) → `LLM_RATE_LIMITED`(SSE `error`. `Retry-After`는 로그로만 남긴다 — SSE 응답 헤더는 이미 전송돼 실을 수 없다, 정정 2026-09-07), 타임아웃 → 기존 `LLM_STREAM_TIMEOUT`, `stop_reason=refusal` → 기존 "정보 없음" 정책 문구가 아닌 별도 코드 `LLM_REFUSED`로 사용자에게 안내(`stop_details.category` 로그). 스트림 도중 `event: error`도 같은 표로 매핑한다(`overloaded_error`·`rate_limit_error` → `LLM_RATE_LIMITED`, `authentication_error`·`permission_error` → `LLM_CONFIGURATION_INVALID`, 나머지 → `LLM_STREAM_FAILED`). 새 코드가 SSE `error`에 실리도록 `ChatMessageService`는 어댑터가 던진 `ChatException`의 코드를 그대로 전달한다(로드맵 7절 1번 예외, 2026-09-07) |
| 계측 | 정정 2026-09-07: 현행 코드에 "단계별 시간 기록"은 없다. `B1`은 `message_start.message.usage`(`input_tokens`·`cache_read_input_tokens`)와 `message_delta.usage`(`output_tokens`)를 INFO 로그로만 남기고, 저장·집계는 `B2`에서 한다 |
| 재검색 루프(선택) | `research-loop.enabled=true`면 `search_knowledge` 툴을 정의하고 `stop_reason=tool_use` 시 서버가 `PublishedDocumentSearchService`를 실행, `tool_result`를 한 user 메시지로 반환, 최대 2회. 기본 off — 왕복 2회로 TTFT가 늘어나므로(검토 문서 5.6절) 실측 후 결정. → `B1` 범위에서 제외하고 로드맵 `B4`로 분리(2026-09-07) |
| 품질 재검증 | 모델 교체 전 `docs/llm-search-benchmark-independent-30.json` gold set 30문항 + 사람 검수, TTFT 5초 실측(ADR 271 조건). 통과 전 `llm.chat.provider=anthropic`을 운영에 켜지 않는다 |
| 비용 가드 | 질문당 입력 상한(근거 문서 10,000자 유지), 세션당 히스토리 상한(현행 4개 메시지·4,000자), 일일 토큰 예산 로그 경고 |

### 6.3 C안(Workspace BYO 키) 후속

B안이 안정되면 `content_source_authorizations`와 같은 암호화 envelope으로 Workspace별 Anthropic 키를 보관하고 `AnthropicLlmClient`가 Workspace 키를 우선 사용한다. 키 등록·폐기 UI가 필요하므로 별도 Issue·ADR.

### 6.4 탐색 데스크톱 경유 계약 (2026-09-07 개정, 로드맵 트랙 S)

사용자 지시: 서버의 융합·선별을 상위 3개 페이지가 아니라 **유사도 상위 청크 8개**로 늘려 응답하고, 요청은 **웹 → 데스크톱 앱 → 서버(검색) → 데스크톱 → 사용자 LLM**으로 흐른다. 색인(청킹·임베딩·게시)은 바뀌지 않는다. 미결 항목의 기본값은 로드맵 Q21~Q29다.

#### 흐름

1. renderer `streamChatMessageApi` → `window.knotDesktop.chat.ask({sessionId, content})` (IPC, sender 오리진 검증).
2. main → `POST /api/v1/conversations/{sessionId}/search` (Bearer). 서버: 소유자·스냅샷·진행 중 턴 검사(Q23) → 직전 4개 + 질문으로 검색 질의(4,000자) → 넓은 질문 판정 → 벡터 상위 50 + 키워드 상위 50 → 0.35 미만 제거 → 벡터 0.7 + 키워드 0.3 합산 → **청크 단위 상위 8개(페이지 중복 허용, Q29)** → USER 메시지 저장(READY가 아니면 안내 ASSISTANT도 같은 트랜잭션. 정정 2026-09-07, 로드맵 Q30: 검색이 실패하면 아무것도 저장하지 않는다) → 응답.
3. main이 `system` = `groundingRules` + `[근거 문서 n] 제목/문서 ID/문서 링크/내용` × 8(현행 `SearchContext.groundingPrompt`와 같은 형식), `messages` = `GET /api/v1/conversations/{sessionId}` 이력으로 사용자 LLM에 스트리밍 요청.
4. delta마다 renderer에 `chunk {delta}`. 취소는 `cancel()` → LLM 요청 중단, 저장 없음.
5. 종료 시 main → `POST /api/v1/conversations/{sessionId}/messages/assistant` → `201 {messageId}` → renderer에 `complete {messageId}`.
6. renderer → `GET /api/v1/messages/{id}/sources`(최대 8건, `chunkIndex` 포함) → 페이지로 묶어 "찾은 문서".

#### 검색 API

| 항목 | 계약 |
| --- | --- |
| 요청 | `POST /api/v1/conversations/{sessionId}/search`, `{ "content": string }`(1~10,000자), `Authorization: Bearer` |
| 응답 READY | `{ "status": "READY", "userMessageId", "groundingRules": string, "chunks": [ { "importRunId", "importedPageId", "chunkIndex", "title", "sourceUrl", "content", "score" } × ≤8 ] }`. 점수 내림차순. `content`는 `max-context-characters=12000`에 맞춰 서버가 자른다(Q28) |
| 응답 READY 아님 | `{ "status": "NO_RESULT" 또는 "NEEDS_CLARIFICATION", "userMessageId", "assistantMessageId", "fallbackAnswer" }`. 서버가 안내 문구를 ASSISTANT로 저장한 뒤 응답한다. 데스크톱은 `chunk` 1개 + `complete {assistantMessageId}`로 중계하고 LLM을 부르지 않는다 |
| 오류 | 403 `CHAT_ACCESS_DENIED`, 404 `CHAT_SESSION_NOT_FOUND`, 409 `CHAT_DOCUMENTS_NOT_READY`, 409 `CHAT_TURN_IN_PROGRESS`(Q23), 400 `VALIDATION_ERROR`, 500 `SEARCH_PROVIDER_FAILED`·`SEARCH_CONFIGURATION_INVALID`(로드맵 Q31, 추가 2026-09-07). 데스크톱은 코드·문구를 그대로 `error`로 중계한다 |
| 선별 변경 | `PublishedDocumentSearchService.selectSources`의 페이지 중복 제거를 없애고 `top-k`를 8로. 융합 가중·임계·후보 수는 유지 |

#### 답변 저장 API

| 항목 | 계약 |
| --- | --- |
| 요청 | `POST /api/v1/conversations/{sessionId}/messages/assistant`, `{ "userMessageId", "content", "references": [ { "importRunId", "importedPageId", "chunkIndex", "score" } × ≤8 ] }`. 배열 순서가 rank |
| 검증 | 세션 소유자 · `userMessageId`가 세션의 마지막 메시지이고 답변이 없음(아니면 409 `CHAT_TURN_MISMATCH`) · 근거는 현행 `JdbcSearchReferenceRepository.replace`의 Workspace JOIN `INSERT…SELECT`로만 검증(Q24) · rank 1~8 · 점수 0~1 클램프 |
| 저장 | ASSISTANT 메시지(`generated_by=CLIENT`, Q25) + `search_references` 같은 트랜잭션. 응답 `201 { "messageId" }` |
| 미검증 | 서버가 돌려준 8개의 부분집합인지, 모델이 실제로 그 근거를 읽었는지는 검증하지 않는다(검토 문서 5.4, 로드맵 R17) |

#### 스키마 V14

| 대상 | V13 | V14 |
| --- | --- | --- |
| `search_references.reference_rank` | `CHECK BETWEEN 1 AND 3` | `CHECK BETWEEN 1 AND 8` |
| `search_references` 유일 키 | `UNIQUE (message_id, imported_page_id)` | `chunk_index SMALLINT NOT NULL` 추가(기존 행은 0, 로드맵 Q32) + `CHECK (chunk_index >= 0)`, `UNIQUE (message_id, imported_page_id, chunk_index)` |
| `chat_messages` | — | `generated_by VARCHAR(10) NOT NULL DEFAULT 'SERVER'` + `CHECK (generated_by IN ('SERVER', 'CLIENT'))` |
| `GET /messages/{id}/sources` | 페이지 단위 ≤3 | 청크 단위 ≤8, `chunkIndex` 추가 |

#### 데스크톱 main

| 항목 | 설계 |
| --- | --- |
| 사용자 LLM | (a) 로컬 모델 — LM Studio·Ollama 등 OpenAI 호환 `/chat/completions`, 키 없음. (b) 사용자 본인 API 키 — Anthropic `/v1/messages` 또는 OpenAI 호환 서비스. claude.ai 로그인·구독 OAuth·세션 토큰 중개는 불변 계약 3번·검토 문서 5.1로 제외 |
| 설정 저장 | `userData/llm-settings.json`(provider·baseUrl·model), 키는 `safeStorage` → `userData/llm-key.bin`. `isEncryptionAvailable()` false면 키를 저장하지 않고 메모리로만 |
| 엔드포인트 허용 | `https:` 전체 + `http://localhost`·`http://127.0.0.1`(Q27). 네비게이션 허용 목록과 별개 |
| 동시성·타임아웃 | 세션당 진행 중 요청 1개. 첫 조각까지 30초(Q26) → `LLM_STREAM_TIMEOUT` |
| 오류 매핑 | 6.2 표와 같은 코드. 401/403 → `LLM_CONFIGURATION_INVALID`, 429·529 → `LLM_RATE_LIMITED`, 거부 → `LLM_REFUSED`, 그 외 → `LLM_STREAM_FAILED`. 설정 없음 → `LLM_CONFIGURATION_INVALID` |
| 프롬프트 | 규칙 문장은 서버 응답을 그대로 쓴다. 근거 블록 형식은 현행 `SearchContext.groundingPrompt`와 동일. 세션 이력 전체를 `messages`로 |
| 로깅 금지 | LLM 키·서버 토큰·프롬프트 본문·답변 본문. 사용량(토큰 수)만 INFO |

#### 웹 SPA

| 변경 | 내용 |
| --- | --- |
| 요청 함수 분기 | `streamChatMessageApi`가 `window.knotDesktop?.chat` 있으면 IPC 경로를 async generator로 감싸 같은 `ChatStreamEvent`를 yield. `signal` abort → `cancel()`. 상위 훅(`useSendChatMessageMutation`·`useChatStream`) 무변경 |
| 찾은 문서 | `useSearchReferenceList`의 mock을 `GET /messages/{id}/sources` 조회로. 8건을 페이지로 묶고 페이지 대표 점수는 최고 청크 점수 |
| LLM 설정 화면 | 데스크톱에서만 노출. `knotDesktop.llm`으로 읽고 쓴다. 키 값은 화면에 되돌아오지 않는다 |
| 설정 없음 | 데스크톱에서 키가 필요한 provider인데 키가 없거나 설정이 비면 질문 전송 전에 설정 화면으로 안내 |
| 브라우저 단독 | 기존 SSE 경로 유지(Q22) |

#### 갈림길

| 상황 | 결정 주체 | 웹이 받는 것 |
| --- | --- | --- |
| 검사 실패 | 서버(HTTP 403·409) | `error {code, message}` |
| 넓은 질문·근거 없음 | 서버(안내 문구 저장) | `chunk` 1개 + `complete` |
| READY → LLM 성공 → 저장 성공 | 데스크톱·서버 | `chunk` × n + `complete {messageId}` |
| LLM 실패·타임아웃·취소 | 데스크톱 | `error {code}`(취소는 이벤트 없음). ASSISTANT 저장 없음 |
| 저장 실패 | 서버(HTTP) → 데스크톱 | `error {code}`. 화면의 부분 답변은 남김. 다음 질문 전 재시도(Q23) |

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
| 1 | HTTPS만 로드 | 4.5 오리진 표. `http://localhost:3000`(웹)·`http://localhost:8080`(API)은 `local` 빌드에서만 |
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
| 19 | Fuses | `RunAsNode` off, `EnableNodeOptionsEnvironmentVariable` off, `EnableNodeCliInspectArguments` off, `EnableCookieEncryption` on(`D11` 이후 인증 쿠키는 없지만 OAuth 세션 쿠키가 남는다), `EnableEmbeddedAsarIntegrityValidation` on, `OnlyLoadAppFromAsar` on, `GrantFileProtocolExtraPrivileges` off |
| 20 | API 비노출 | 4.4 인터페이스 외 노출 금지, `ipcRenderer` 원본 금지 |
| + | 환경변수·플래그 | `disable-site-isolation-trials`·`--ignore-certificate-errors` 사용 금지 |
| + | 자격증명 | main이 쿠키 값을 읽지 않는다(`cookies.get` 금지). 토큰은 `safeStorage`로 암호화해 `userData`에만 두고, 로그·크래시 리포트·IPC 인자 로깅에 절대 포함하지 않는다. 2단계에서 renderer 노출까지 없앤다(5.1·5.3) |
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
| **액세스 토큰 저장소(`localStorage` / 데스크톱 preload)와 `Authorization` 헤더 부착** | P1 | `D11`. `httpClient` 인터셉터 + SSE fetch. CSRF 코드 제거 |
| **로그인 리다이렉트 프래그먼트(`#access_token`·`#onboarding_token`) 수신·삭제** | P1 | `D11`. React 렌더 전에 실행해 첫 요청부터 헤더가 붙게 한다 |
| 외부 링크(Notion 페이지 링크 등)를 데스크톱에서 `openExternal`로 | P1 | `target=_blank`는 main이 가로채므로 필수는 아님. UX 통일용 |
| 로그아웃 액션(`POST /api/v1/auth/logout` → `/login`) | P1 | 웹에도 필요한 누락 기능 |
| 딥링크 수신 → 라우터 이동 | P2 | `RouterProvider` 상위에서 `onDeepLink` 구독 |
| 초대 페이지에 "앱에서 열기"(`knot://invite/<token>`) | P2 | 앱 미설치 시 안내 |
| 다운로드 페이지(`/download`, OS 감지) | P1 | 정적 라우트 |
| `<title>` 수정(`Document` → `Knot`) | P1 | 창 제목에 그대로 보인다 |
| CSP 헤더(`_headers`) | P1 병행 | 웹 보안 Issue로 분리 |
| **탐색 요청 함수 데스크톱 분기(`knotDesktop.chat` 있으면 IPC, 없으면 SSE)** | S4 | 6.4. 상위 훅·화면 무변경 |
| **찾은 문서 서버 연동(`GET /messages/{id}/sources` 8건, 페이지로 묶기)** | S4 | 6.4. `useSearchReferenceList` mock 제거 |
| LLM 설정 화면(데스크톱 전용, `knotDesktop.llm`) + 설정 없음 안내 | S4 | 6.4. 키 값은 화면에 되돌아오지 않는다 |

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
| **인증 자격증명을 쿠키 → `Authorization: Bearer`로 전환**(`JwtAuthenticationFilter`, OAuth 성공 핸들러 프래그먼트 전달, `/auth/nickname` 응답 본문 토큰, CSRF·`/auth/csrf`·`AuthCookieManager` 제거, CORS 헤더 교체) | `D11`, P1 | `security`, `cross-boundary`, `core-flow` | 5.1. 프론트와 동시 배포. 인수 테스트 전량이 쿠키 대신 헤더를 쓰도록 바뀐다 |
| 액세스 토큰의 회원 존재 확인(`JwtAuthenticationFilter` → `MemberService.existsById`, 없으면 401) | `C1` 보강 | `security` | 5.1 회원 확인 행, 로드맵 Q41. 회원이 없는 토큰을 인수 테스트가 401로 검증한다. `A6`의 서버 세션 조회가 이 자리를 대체한다 |
| 채팅·임베딩 provider 설정 분리(`llm.chat.provider`, `llm.embedding.provider`) | B안 선행 | `shared` | 기존 `openai-compatible` 동작 불변 |
| `AnthropicLlmClient`/`AnthropicLlmStream` + 설정 키 + 오류 코드 | B안 | `external`, `shared` | ADR(검토 문서 7절 B vs C) |
| **임베딩 Gemini 어댑터** `GeminiEmbeddingClient` + `llm.gemini.*` 설정 키 + `DocumentEmbeddingClient.embed(texts, EmbeddingTask)` 시그니처(색인·질의 `taskType` 분리) | B5 | `external`, `shared`, `data` | 6.2 임베딩 행. V13 차원 1,024 유지(`outputDimensionality` + L2 정규화). 기존 색인은 동기화 재실행으로 재색인(로드맵 Q39) |
| 임베딩 배치 기본값 64 → 16(`llm.search.embedding-batch-size`) | B5 정정 | `external` | 로컬 실측(2026-09-08) 배치 64가 Gemini 429 `RESOURCE_EXHAUSTED`로 Notion 동기화 전체를 실패시킴. 로드맵 Q37·U25 |
| Gemini 색인 배치 429·503 지수 백오프 재시도(`llm.gemini.retry-max-attempts`·`retry-initial-delay`, 질의는 제외) | B5 정정 | `external` | 배치 16으로도 무료 티어 분당 한도(약 32청크)에 3번째 배치가 걸림. 로드맵 Q42·R22 |
| 사용량 계측 컬럼(입력·출력·캐시 토큰) | B안 | `data` | Flyway |
| `device_sessions`·인가 코드 저장, 디바이스 토큰 API 5종 | 2단계 | `security`, `data`, `cross-boundary`, `core-flow` | 인터뷰 + Grill + ADR(314 보완) |
| `typ=DEVICE_ACCESS`·`sid` 검증 추가, OAuth resolver·성공 핸들러 `client=desktop` 분기 | 2단계 | `security` | 동일 ADR. CSRF 매처 작업은 `D11`에서 사라졌다 |
| CORS 허용 메서드에 DELETE(기기 세션 삭제를 웹에서 쓸 때) | P3 | `security` | 없어도 POST 대체 가능 |
| 로그아웃 응답 204(302 대신) | P1 | `cross-boundary` | SPA가 XHR로 호출하므로. `D11`에 흡수됐다 |
| 원격 MCP 서버(Spring AI) | P3 선택 | `external`, `security` | 별도 기획 |
| **탐색 검색 API `POST /conversations/{sessionId}/search`**(청크 단위 상위 8, 규칙 문장 응답, READY 아니면 안내 문구 저장) + `top-k=8`·`max-context-characters=12000` | S1 | `core-flow`, `data`, `shared` | 6.4. 불변 계약 1·2 개정 ADR |
| **V14**: `search_references` rank 1~8·`chunk_index`·유일 키, `chat_messages.generated_by` | S1 | `data` | Flyway. 6.4 |
| **답변 저장 API `POST /conversations/{sessionId}/messages/assistant`** + 출처 조회 8건·`chunkIndex` | S2 | `data`, `security`, `core-flow` | 6.4. Workspace JOIN 검증 재사용 |

변경하지 않는 것: 색인(청킹·임베딩·게시), 하이브리드 검색의 융합 가중·임계·후보 수, 문서 준비 게이트, 규칙 문장, 세션 모델, Notion 연결, JWT 발급 로직·클레임. 브라우저 단독용 SSE 경로는 자격증명 헤더 교체와 근거 8청크 외에 바꾸지 않는다(로드맵 Q22).

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
| I15 | `[BE] 인증 자격증명 쿠키 → Bearer JWT 전환` | be | P1 | `security`, `cross-boundary`, `core-flow` | 필요 | D11(쿠키 유지 vs Bearer 전환 vs 이중 경로), ADR 314 보완 | — |
| I16 | `[FE] 액세스 토큰 저장소와 Authorization 헤더` | fe | P1 | `security`, `cross-boundary` | 필요 | I15와 같은 ADR | I15 |
| I17 | `[FE] 데스크톱 토큰 저장(preload · safeStorage)` | fe | P1 | `security` | 필요(짧게) | I15와 같은 ADR | I16, I2 |
| I7 | `[BE] 채팅 LLM Anthropic Messages API 어댑터` | be | B안 | `external`, `shared` | 필요 | B안 vs C안(검토 문서 7절), SDK vs HttpClient(→ 로드맵 Q20으로 확정) | provider 분리 |
| I8 | `[BE] 채팅·임베딩 provider 설정 분리` | be | B안 선행 | `shared` | 필요(짧게) | 없음 | — |
| I9 | `[BE] 데스크톱 디바이스 토큰 인증 경로(코드 교환·refresh rotation·폐기·기기 목록)` | be | P2 | `security`, `data`, `cross-boundary`, `core-flow` | 필요(인터뷰 6항목 전부) | D4(패턴 A·B vs C vs D), ADR 314 보완 | I2 |
| I10 | `[FE] 데스크톱 시스템 브라우저 로그인·loopback·딥링크 콜백·토큰 저장` | fe | P2 | `security`, `cross-boundary` | 필요 | I9와 같은 ADR | I9 |
| I11 | `[FE] knot:// 딥링크: 초대·채팅 진입` | fe | P2 | `core-flow` | 필요 | 스킴 이름(`knot` vs reverse-domain) | I2 |
| I12 | `[FE] 트레이·글로벌 단축키 퀵 질문 창` | fe | P2 | `core-flow` | 필요(짧게) | 없음 | I2 |
| I13 | `[FE] Notion 동기화 완료 알림(폴링)` | fe | P2 | 없음 → Lightweight 가능 | 판정기 결과 따름 | 없음 | I2 |
| I14 | `[FE] 기기 목록·원격 로그아웃 UI` | fe | P3 | `security` | 필요 | 없음 | I9 |
| I18 | `[BE] 탐색 검색 API(청크 상위 8)와 V14` | be | S1 | `core-flow`, `data`, `shared` | 필요 | D3 개정(서버 LLM vs 데스크톱 사용자 LLM), Q21·Q28·Q29 | I8 |
| I19 | `[BE] 데스크톱 생성 답변·근거 저장 API` | be | S2 | `data`, `security`, `core-flow` | 필요 | I18과 같은 ADR, Q23·Q24·Q25 | I18 |
| I20 | `[FE] 데스크톱 탐색 IPC와 사용자 LLM 클라이언트` | fe | S3 | `security`, `external`, `core-flow` | 필요 | I18과 같은 ADR, Q26·Q27 | I1, I17, I18 |
| I21 | `[FE] 웹 탐색 전송 경로 분기·찾은 문서 연동·LLM 설정 화면` | fe | S4 | `core-flow` | 필요(짧게) | 없음 | I19, I20 |
| I22 | `[BE][FE] 청크 8개 기준 gold set 재측정과 TTFT 계측 전달` | be, fe | S5 | `core-flow` | 판정기 결과 따름 | 없음 | I21 |

Issue 생성은 사용자가 명시적으로 허용하고 판정기가 `pass`·`publish_ready=true`를 반환한 경우에만 `--publish`로 수행한다. 이 표는 초안일 뿐 snapshot이 아니다.

`I1~I22`는 이 문서의 초안 번호다. 실제 실행 단위·상태·의존은 [로드맵](./electron-desktop-app-roadmap.md) 4절의 `A*`·`W*`·`B*` ID를 쓰며, 대응표는 로드맵 4절에 있다.

## 15. 리스크와 미결 결정

| # | 리스크·미결 | 영향 | 해소 방법·담당 |
| --- | --- | --- | --- |
| R1 | GitHub이 Electron 창(embedded UA) 로그인을 경고·차단할 수 있음(정책 미확인) | 1단계 인증 불가 → 2단계 선행 | I1 스파이크에서 실측 |
| R2 | Notion OAuth 302 체인 도메인이 허용 목록과 다를 수 있음 | 연결 실패 | I1에서 실제 도메인 기록. 2026-09-08 실측: 동의 화면이 `app.notion.com`이라 실제로 달랐고 목록에 추가했다(4.5). 동의 이후 홉은 재측정 |
| R3 | 운영 API 오리진이 저장소에 없음(`API_BASE_URL_PROD`) | prod 빌드 허용 목록 | 팀에 값 확인, `desktop/src/shared/env.ts`에 고정 |
| R4 | access 1시간·리프레시 없음 | 1단계 UX 저하 | 2단계(I9)로 해소. 그전까지 재로그인 안내 |
| R14 | `D11`으로 토큰이 JS에서 읽히므로 XSS 하나가 곧 토큰 유출(`HttpOnly` 격리 상실) | 계정 탈취 | CSP(9.4)·1시간 만료·의존성 감사로 완화, 2단계에서 데스크톱 토큰을 main으로 이관. 웹은 리프레시 토큰 도입 시 액세스 토큰을 메모리로 내릴 수 있는지 재검토 |
| R15 | `D11`은 프론트·백엔드 동시 배포가 필요하고 호환 기간이 없음 | 배포 순간 전 사용자 재로그인, 순서가 어긋나면 401 구간 발생 | 백엔드 먼저 배포하고 프론트를 바로 잇는다. 구버전 SPA는 401 → `/login`으로 떨어지므로 데이터 손상은 없다 |
| R5 | macOS 서명·공증 계정 부재 | 알림·`safeStorage`(`D11`의 데스크톱 토큰 저장)·쿠키 암호화·자동 업데이트 불가. Sequoia부터 미공증 앱은 시스템 설정에서 수동 승인 필요 | Apple Developer Program(US$99/년) 개설 결정, 소유 주체·인증서 보관 |
| R6 | Windows 서명: Azure Artifact Signing은 조직 계정만(개인 개발자는 미국·캐나다만, 한국 조직 자격은 문서 불일치), Basic US$9.99/월. EV는 SmartScreen 이점 없음 | 미서명 시 "Windows protected your PC" 경고, 기관 관리 PC·Smart App Control은 차단 가능 | 조직 계정 가능 여부 확인 후 미서명 출시 + 안내 vs Artifact Signing 결정 |
| R7 | 크래시 수집 서버·개인정보 | 운영 가시성 | Sentry vs 자체 vs 미수집 결정, 고지 문구 |
| R8 | Cloudflare `_headers` CSP가 Emotion 인라인 스타일과 충돌 | 웹 스타일 깨짐 | I5에서 `style-src` 정책 실험 |
| R9 | Electron 8주 메이저 주기 | 유지보수 부담 | Renovate + EOL 알림, 분기 1회 업그레이드 |
| R10 | `desktop/` 추가로 Governance area 확장 요구 | 규칙 변경 | `fe`로 시작, 필요 시 conventions 변경 Issue |
| R11 | 단일 NCP 인스턴스 전제(인메모리 스트림 레지스트리·인가 코드) | 수평 확장 시 재설계 | 현행과 동일 제약, ADR 212·328 |
| R12 | Anthropic 모델 교체 시 품질·TTFT 회귀 | 답변 품질 | gold set 30문항·TTFT 실측 통과 전 미전환 |
| R13 | Tauri·PWA 재검토 조건 | 셸 교체 | 배포 크기 최우선·Rust 인력·WebKit QA·모바일 계획이 동시에 생길 때만(Tauri), 데스크톱 고유 기능 요구가 사라질 때(PWA) |
| R17 | 데스크톱이 만든 답변을 서버가 검증할 수 없어 피드백·품질 평가 데이터의 신뢰가 떨어짐 | 임의 문장이 ASSISTANT로 저장될 수 있음 | `generated_by=CLIENT` 표시(Q25), Workspace JOIN 검증(Q24). 후보 집합 보관은 후속 |
| R18 | 브라우저 단독(SSE)·데스크톱(IPC) 두 탐색 경로 유지 비용 | 서버 LLM 어댑터와 데스크톱 LLM 클라이언트를 함께 유지 | 규칙 문장·선별·저장 검증을 서버 한 곳에 두어 중복을 줄인다. 웹 탐색 비활성은 Q22 대안 |
| R19 | 근거 3페이지 → 청크 8개, 모델이 사용자마다 달라 gold set 결과가 이전과 비교되지 않음 | 품질 판정 불가 | 기준 모델 하나로 재측정(`S5`, GS) |
| R21 | 허용 목록 밖 홉을 셸이 외부 브라우저로 빼면, 이동을 시작한 SPA는 이동 대기 상태(`isRedirecting`)에 갇혀 복구 경로가 없음 | 연결 버튼 무한 로딩(2026-09-08 관측) | 목록을 실측대로 유지(U2). 창 포커스 복귀·타임아웃으로 대기 상태를 푸는 FE 후속 |

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
