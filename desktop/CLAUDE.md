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
- Knot은 **LLM을 호출하지 않는다**(불변 계약 2번, 2026-09-09 개정). main·MCP 서버 프로세스·
  renderer 어디에서도 LLM API를 부르지 않는다. 답변은 사용자의 CLI 코딩 에이전트가 만들고, 이
  셸은 로컬 MCP 서버의 `search_documents`·`list_workspaces` 도구로 서버 검색 결과만 돌려주고, `show_answer`(`S10`)는
  에이전트가 이미 만든 답변을 서버에 저장해 앱 화면에 보여 줄 뿐 답을 만들지 않는다.
  도구 실행(서버 API 호출)은 main만 하며 MCP 서버 프로세스는 서버 액세스 토큰을 모른다.
  서버 액세스 토큰·연결 토큰은 main 밖(renderer·MCP 프로세스·도구 결과·로그)으로 내지 않는다.
- Knot은 **어떤 LLM 자격증명도 저장·중개·요구하지 않고, CLI 바이너리를 실행·동봉·변경하지
  않는다**(3번, 2026-09-09 개정). `~/.claude`·`~/.codex`·`~/.gemini`·Keychain·
  `CLAUDE_CODE_OAUTH_TOKEN`·`ANTHROPIC_API_KEY`·`OPENAI_API_KEY`·`GEMINI_API_KEY`를 읽거나 쓰지
  않고, `claude`·`codex`·`gemini`를 `child_process`로 실행하지 않는다. `claude -p` 서브프로세스와
  Agent SDK 내장은 사용자가 거부한 방식이라 다시 제안하지 않는다(`S6` 폐기). 연결은 사용자가
  자기 CLI에 Knot MCP 서버와 스킬을 등록하는 것으로만 이뤄진다.
- 로컬 MCP 서버는 `127.0.0.1`에만 바인딩하고, `Origin` 헤더가 있는 요청과 연결 토큰이 없거나
  다른 요청을 거부한다(로드맵 Q47·Q48). 이 검사를 빼거나 `0.0.0.0`에 바인딩하지 않는다.
- Fuse 7종(`forge.config.ts`)을 낮추지 않는다.

## 구조

| 위치 | 책임 |
| --- | --- |
| `src/main/` | 창·메뉴·네비게이션 정책·IPC·로그. Node 환경 |
| `src/main/chat/` | `knotApi.ts`(Bearer 서버 API 클라이언트 — 워크스페이스 목록·Workspace 검색·세션 생성·턴 저장). `S8`·`S10`의 도구 실행이 쓴다. 폐기된 `S3` 잔재(`prompt`·`chatService`·`src/main/llm/`)는 2026-09-09 제거했고 `test/s3Residue.test.ts`가 다시 생기지 않게 지킨다 |
| `src/main/agent/` (`S8`) | main 쪽 브리지: `bridge.ts`(`utilityProcess` 기동·`MessageChannelMain`·클립보드, Electron 접착), `bridgeCore.ts`(`MessagePort` 왕복·동시 4개·로그 필드 제한), `bridgeConfig.ts`(`agent-bridge.json` 0600·연결 토큰·포트), `toolExecutor.ts`(도구 입력 재검사·서버 호출·결과 조립. `show_answer`는 세션 생성 → 턴 저장 → `presentAnswer` 콜백 순), `registration.ts`(세 CLI 스니펫·스킬 설치 명령·토큰 가림), `instructions.ts`(MCP `instructions`). 순수 로직은 Electron을 import 하지 않아 vitest로 검증한다 |
| `src/mcp/` (`S8`) | `utilityProcess` 엔트리 `index.ts`(parentPort `start` 메시지 수신) + `server.ts`(`@modelcontextprotocol/sdk` 무상태 Streamable HTTP, `127.0.0.1:<port>/mcp`, 도구 정의 3개 — `list_workspaces`·`search_documents`·`show_answer`) + `guard.ts`(Origin 있으면 403·Host·Bearer 연결 토큰) + `portRequester.ts`(main 위임·35초 타임아웃). 서버 액세스 토큰을 받지 않고 LLM을 부르지 않는다 |
| `resources/skills/knot/SKILL.md` (Q50) | Knot 스킬. Agent Skills 표준 필드만. 앱 시작 시 `userData/skills/knot/SKILL.md`로 복사해 두고, 사용자 홈(`~/.claude/skills`·`~/.agents/skills`)에는 자동으로 쓰지 않는다 — 메뉴·연결 안내 화면이 `cp` 명령을 복사해 준다 |
| `src/main/auth/` (`A7`) | 시스템 브라우저 로그인·토큰 갱신: `pkce`·`authorizeUrl`·`loopbackServer`(127.0.0.1 임의 포트, 콜백 1회)·`deviceTokenApi`(교환·갱신·폐기)·`deviceSessionStore`(`auth-session.bin`)·`loginFlow`(상태 기계, Electron 미의존)·`bearerInjector`(API 오리진 XHR·fetch에만 주입, Q55)·`desktopAuth`(Electron 접착 — `signed-out`이면 쿠키 삭제 + `/login`). 코드·state·토큰 값은 로그·IPC 인자에 싣지 않는다 |
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
- 새 의존성은 기획서 9.2의 목록을 따른다. `keytar`와 클라이언트 LLM SDK(`@anthropic-ai/*`·
  `openai`·`@google/genai`)는 금지다. `@modelcontextprotocol/sdk`는 LLM SDK가 아니라 허용된다
  (로드맵 Q47).
- 주석·커밋·문서는 한국어로 쓴다. 따옴표는 큰따옴표, 들여쓰기 2칸(`.prettierrc`).

## 검증

`pnpm typecheck && pnpm test`를 통과시킨 뒤 PR을 올린다. 보안에 닿는 변경은
기획서 8절 체크리스트 20항목을 PR 본문에서 확인하고, 패키징 변경은
`npx @electron/fuses read`로 Fuse를 재확인한다.
