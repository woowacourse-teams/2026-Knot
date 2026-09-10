# desktop/ 작업 규칙

`frontend/.claude/rules`는 React 규칙이라 여기 적용되지 않는다. 이 디렉터리는 Electron
main·preload 프로세스 코드다.

## 문서 우선

작업 전에 [로드맵](../docs/electron-desktop-app-roadmap.md)을 읽는다. 코드와 문서가
어긋나면 **문서가 맞다.** 실측으로 다른 사실을 확인하면 근거(경로:라인 또는 관찰 로그)와
정정 날짜를 달아 문서를 먼저 고치고 그대로 진행한다. 루트 `CLAUDE.md`의 규칙이 그대로
적용된다.

## 협상 대상이 아닌 것 (로드맵 7절 불변 계약)

- `nodeIntegration: false`, `contextIsolation: true`, `sandbox: true`,
  `webviewTag: false`. 창을 추가할 때도 같다.
- 네비게이션 허용 목록은 `will-navigate`와 `will-redirect` **양쪽**에 건다.
  OAuth는 302 체인이라 `will-navigate`만으로는 뚫린다(2026-09-06 `A1` 실측).
- 모든 `ipcMain.handle`은 `event.senderFrame`의 오리진을 검사한다. `senderFrame`이
  `null`이면 거부한다.
- preload는 `src/shared/api.ts`의 인터페이스만 노출한다. `ipcRenderer` 원본과
  `ipcRenderer.on`의 `event` 객체는 renderer로 넘기지 않는다.
- `shell.openExternal`은 `https:`·`mailto:`만 받는다.
- 2단계 로그인 화면은 **메인 창 안 `WebContentsView`**다(재개정 2026-09-10, 로드맵 Q68).
  로그인 때문에 `BrowserWindow`를 새로 만들지 않는다 — 자식 창·모달·시스템 브라우저
  전부 금지다. 로그인 뷰에서는 새 창도 만들지 않는다(로드맵 Q69 — 허용 목록 안이면 같은
  뷰에서 이동, 밖이면 거부). loopback 콜백 오리진은 **그 뷰에만** 한시로 허용하고, 빌드
  상수 허용 목록에 넣지 않는다.
- 탐색 답변은 **앱이 사용자 본인의 Claude 구독으로 앱 안에서 만든다**(불변 계약 2번,
  2026-09-10 개정, 트랙 L). main이 Claude Code OAuth 흐름으로 받은 구독 토큰으로
  `api.anthropic.com`을 직접 부르고, 서버는 검색·근거·게이트·저장을 소유한다(기획서 6.5,
  로드맵 Q61). 앱이 만든 답변·출처도 서버 턴 API를 거쳐 `generated_by=CLIENT`로만 저장한다.
  구독 미로그인·`401`·크레딧 소진은 서버 SSE 경로로 폴백한다(Q22).
  **구독 access·refresh 토큰(`sk-ant-oat…`·`sk-ant-ort…`)은 main의 `safeStorage`
  (`subscription-auth.bin`)에만 두고 renderer·IPC 응답·로그·크래시 리포트에 싣지 않는다.**
  서버 액세스 토큰·연결 토큰도 같다.
- **불변 계약 3번(LLM 자격증명·CLI 바이너리 무접촉)은 2026-09-10 사용자 지시로 삭제됐다.**
  앱은 이제 사용자 Claude 구독 자격증명을 의도적으로 획득·보관·사용한다. 다만 `claude`·`codex`·
  `gemini` **바이너리를 `child_process`로 실행하는 것은 계속 하지 않는다**(사용자가 반복 거부,
  기획서 2.2 비목표). `spawn(`·`execFile(`을 새로 들이지 않는다.
- 로컬 MCP 서버(`S8`~`S10`)와 `show_answer`는 **선택적 부가 진입점**으로 유지한다. MCP 서버
  프로세스는 서버 액세스 토큰도 구독 토큰도 모른다 — 도구 실행(서버 API 호출)은 main만 한다.
- 로컬 MCP 서버는 `127.0.0.1`에만 바인딩하고, `Origin` 헤더가 있는 요청과 연결 토큰이 없거나
  다른 요청을 거부한다(로드맵 Q47·Q48). 이 검사를 빼거나 `0.0.0.0`에 바인딩하지 않는다.
- Fuse 7종(`forge.config.ts`)을 낮추지 않는다.

## 구조

| 위치 | 책임 |
| --- | --- |
| `src/main/` | 창·메뉴·네비게이션 정책·IPC·로그. Node 환경 |
| `src/main/chat/` | `knotApi.ts`(Bearer 서버 API 클라이언트 — 워크스페이스 목록·Workspace 검색·세션 검색·세션 생성·턴 저장). `S8`·`S10`의 도구 실행과 트랙 L의 근거 조회·턴 저장이 함께 쓴다 |
| `src/main/llm/` (`L1`·`L2` 구현 2026-09-09) | 사용자 Claude 구독 경로. `L1`: `subscriptionOAuth.ts`(인가 URL·`platform.claude.com/v1/oauth/token` 교환·갱신, RFC 6749 오류 중계)·`subscriptionStore.ts`(`safeStorage` `subscription-auth.bin`)·`llmSettings.ts`(`subscription-settings.json`, Q62 모델·effort 허용 목록 — `llm-settings.json`은 `agent/bridge.ts`가 S3 잔재로 지우므로 쓰지 않는다)·`subscriptionFlow.ts`(상태 기계, Electron 미의존 — 로그인·state 대조·만료 5분 전 갱신·`invalid_grant`면 삭제·`getAccessToken`·`recordAnswer`)·`desktopLlm.ts`(접착 — `claude.ai` 오리진만 `shell.openExternal`, `knot:llm-status-changed` 방송). `L2`: `sseParser.ts`(증분 SSE 파서)·`messagesClient.ts`(`POST api.anthropic.com/v1/messages` stream, Node `fetch` 직접 호출, 헤더는 로드맵 Q61 c, request/stream 단계 오류·헤더 30초·이벤트 사이 60초)·`promptAssembler.ts`(서버 `groundingRules` + 근거 블록 → `system`, 히스토리 4개·4,000자 → `messages`)·`answerFlow.ts`(`S7` 검색 → 히스토리 → 구독 호출 → `S2` 턴 저장, 폴백 판정은 Q66 — 첫 chunk 전의 모델 쪽 실패만 `fallback: true`)·`desktopLlm.ts`의 스트림 관리(`requestId`·취소·동시 4개·앱 종료 시 abort). `L3`: `llmSettings.parseSettingsUpdate`(허용 목록 재검사)·`desktopLlm.getSettings/updateSettings`·`subscriptionFlow.emitStatus`(모델 변경을 상태 이벤트로), 설정 화면은 웹 `/claude-subscription`이고 앱 메뉴 `Claude 구독 → 구독 설정 열기`가 진입점이다(로드맵 Q64·Q67). 구독 토큰은 이 디렉터리 밖으로 나가지 않는다 — `getAccessToken()`의 반환값을 로그·IPC·상태 객체에 싣지 않는다 |
| `src/main/agent/` (`S8`) | main 쪽 브리지: `bridge.ts`(`utilityProcess` 기동·`MessageChannelMain`·클립보드, Electron 접착), `bridgeCore.ts`(`MessagePort` 왕복·동시 4개·로그 필드 제한), `bridgeConfig.ts`(`agent-bridge.json` 0600·연결 토큰·포트), `toolExecutor.ts`(도구 입력 재검사·서버 호출·결과 조립. `show_answer`는 세션 생성 → 턴 저장 → `presentAnswer` 콜백 순), `registration.ts`(세 CLI 스니펫·스킬 설치 명령·토큰 가림), `instructions.ts`(MCP `instructions`). 순수 로직은 Electron을 import 하지 않아 vitest로 검증한다 |
| `src/mcp/` (`S8`) | `utilityProcess` 엔트리 `index.ts`(parentPort `start` 메시지 수신) + `server.ts`(`@modelcontextprotocol/sdk` 무상태 Streamable HTTP, `127.0.0.1:<port>/mcp`, 도구 정의 3개 — `list_workspaces`·`search_documents`·`show_answer`) + `guard.ts`(Origin 있으면 403·Host·Bearer 연결 토큰) + `portRequester.ts`(main 위임·35초 타임아웃). 서버 액세스 토큰을 받지 않고 LLM을 부르지 않는다 |
| `resources/skills/knot/SKILL.md` (Q50) | Knot 스킬. Agent Skills 표준 필드만. 앱 시작 시 `userData/skills/knot/SKILL.md`로 복사해 두고, 사용자 홈(`~/.claude/skills`·`~/.agents/skills`)에는 자동으로 쓰지 않는다 — 메뉴·연결 안내 화면이 `cp` 명령을 복사해 준다 |
| `src/main/auth/` (`A7`) | 메인 창 안 로그인 뷰(재개정 2026-09-10, 로드맵 Q68 — 2026-09-09 판은 자식 창, 그 전은 시스템 브라우저)·토큰 갱신: `pkce`·`authorizeUrl`·`loopbackServer`(127.0.0.1 임의 포트, 콜백 1회)·`loginView`(메인 창 `contentView`의 `WebContentsView`, 상단 44px 헤더 띠·`resize` 추적·`Esc` 취소·새 창 금지, loopback 오리진을 그 뷰에만 한시 허용)·`deviceTokenApi`(교환·갱신·폐기)·`deviceSessionStore`(`auth-session.bin`)·`loginFlow`(상태 기계, Electron 미의존)·`bearerInjector`(API 오리진 XHR·fetch에만 주입, Q55)·`desktopAuth`(Electron 접착 — `signed-out`이면 쿠키 삭제 + `/login`). 코드·state·토큰 값은 로그·IPC 인자에 싣지 않는다 |
| `src/main/deepLink.ts` (`A8`) | `knot://` 파서(순수)·라우터(웜 스타트 이벤트 + 60초 보류, 콜드 스타트 보류)·`open-url`/`second-instance`/`process.argv` 수신·스킴 등록. `auth/callback`은 main에서만 소비한다. `dispatch`는 `show_answer`·알림 클릭이 같은 경로로 쓴다 |
| `src/main/tray.ts`·`quickAsk.ts` (`A9`) | 트레이 메뉴·글로벌 단축키(`CommandOrControl+Shift+K`)·퀵 질문 창(웹 `/workspace/:id/chat` 로드, 같은 세션·preload·보안 webPreferences)·마지막 워크스페이스(`last-workspace.json`) |
| `src/main/notifications.ts` (`A10`) | `webRequest.onCompleted`로 동기화 시작·조회 요청의 URL·상태 코드만 보고 main이 Bearer로 폴링 → OS 알림·Dock 배지. preload `notifications.show` 입력 재검사 |
| `src/main/windowState.ts`·`updatePolicy.ts`·`updater.ts` (`A2`·`A4`) | 창 상태 복원(`window-state.json`, 디스플레이 밖이면 위치 버림), 자동 업데이트 정책(패키징 + macOS/Windows + prod만, Q59)과 `update-electron-app` 접착 |
| `src/preload/` | `contextBridge`로 `window.knotDesktop` 노출. **CJS 단일 번들**(sandbox preload는 ESM 불가) |
| `src/shared/api.ts` | preload 계약. 웹 SPA가 **복사**해 쓰므로 다른 파일을 import 하지 않는다 |
| `src/shared/env.ts` | 오리진 표(기획서 4.5). 허용 목록의 근거 |
| `test/` | vitest. main의 순수 함수와 `src/mcp`(실제 HTTP + 공식 SDK 클라이언트) — `electron`·`electron-log`는 `vi.mock`한다. `helpers/portPair.ts`가 `MessagePortMain` 한 쌍을 흉내 낸다 |

`renderer/`는 없다. 원격 로드이므로 renderer 빌드가 없고 `resources/offline.html`만 로컬이다.

## 코드

- 환경 값은 빌드 시 상수(`__KNOT_ENV__` 등, `src/shared/build-globals.d.ts`)로 받는다.
  런타임 `process.env` 조회로 바꾸지 않는다.
- `electron-log`의 `log.initialize()`를 호출하지 않는다. renderer가 원격 웹사이트라
  사이트 XSS에 로그 쓰기 능력을 주게 된다.
- 새 의존성은 기획서 9.2의 목록을 따른다. `keytar`는 금지(`safeStorage`를 쓴다).
  `@modelcontextprotocol/sdk`는 허용된다(로드맵 Q47). 2026-09-10 개정: 트랙 L의
  Anthropic 호출은 **SDK 없이** Node `fetch`/`undici`로 `POST /v1/messages`를 직접 부르고
  SSE를 직접 판다 — 백엔드 `B1`이 같은 이유로 JDK `HttpClient`를 골랐고(로드맵 Q20), 구독
  OAuth 헤더(`anthropic-beta`·`user-agent: claude-cli`)를 SDK가 그대로 실어 주지 않는다.
  `@anthropic-ai/sdk` 도입은 이 헤더를 넣을 수 있음이 확인되기 전까지 하지 않는다.
- 주석·커밋·문서는 한국어로 쓴다. 따옴표는 큰따옴표, 들여쓰기 2칸(`.prettierrc`).

## 검증

`pnpm typecheck && pnpm test`를 통과시킨 뒤 PR을 올린다. 보안에 닿는 변경은
기획서 8절 체크리스트 20항목을 PR 본문에서 확인하고, 패키징 변경은
`npx @electron/fuses read`로 Fuse를 재확인한다.

**`test/agentResidue.test.ts`**(2026-09-09 `L1` 착수 때 `s3Residue.test.ts`에서 범위를 좁혀 이름을 바꿈)가
지키는 것: (1) `src/mcp`·`src/main/agent`에는 LLM API 호출·구독 토큰 접근(`api.anthropic.com`·`/v1/messages`·
`sk-ant-`·`src/main/llm`·`subscription-auth`)이 없다, (2) `src` 전체에 `child_process`·`spawn(`·`execFile(`과
사용자 CLI 자격증명 파일(`~/.claude/.credentials.json`·`~/.claude.json`·`CLAUDE_CODE_OAUTH_TOKEN`)·Keychain·`keytar`
접근이 없다(바이너리 실행은 여전히 비목표, 기획서 2.2), (3) preload 계약에 `agent?:`·`llm?:`이 있고 폐기된 `S3`
모양(`chat?:`·`UserLlmSettings`)은 없다. `registration.ts`의 스킬 설치 스니펫(`~/.claude/skills`·`~/.codex/config.toml`)은
사용자가 복사해 쓰는 문자열이라 허용한다. `src/main/llm/`을 고칠 때 이 테스트의 (1)을 약화시키지 않는다.
