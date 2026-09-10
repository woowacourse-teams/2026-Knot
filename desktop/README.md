# Knot 데스크톱 셸

`https://knoted.kr` 웹 앱을 원격 로드하는 Electron 셸이다. 탐색(채팅)의 답변은 **앱이 사용자
본인의 Claude 구독으로 앱 안에서 만든다**(기획서 6.5, 2026-09-10 개정, 로드맵 트랙 L). main이
Claude Code OAuth 흐름으로 받은 구독 토큰으로 `api.anthropic.com`을 직접 부르고, 서버는 검색·
근거·게이트·저장을 맡는다. 구독이 없거나 크레딧이 소진되면 서버 SSE 경로로 폴백한다(로드맵 Q22).
이 형태는 Anthropic `legal-and-compliance`가 금지하는 것에 해당하며 재량 허용 구간에서 운영하는,
사용자가 감수하기로 한 정책 리스크다(로드맵 R28·R29, 게이트 GL). 터미널의 CLI 코딩 에이전트가
붙는 **로컬 MCP 서버**(기획서 6.4)는 선택적 부가 진입점으로 남는다. 셸이 더하는 것은 상시 실행·
딥링크·알림·퀵 질문 창·자동 업데이트, 그리고 이 앱 안 구독 탐색이다.

**작업 전에 [실행 정본 로드맵](../docs/electron-desktop-app-roadmap.md)을 먼저 읽는다.**
설계 근거는 [기술 기획서](../docs/electron-desktop-app-tech-plan.md), 조사 사실은
[지식 문서](../docs/electron-desktop-app-knowledge.md)에 있다. 코드와 문서가 어긋나면
문서가 맞다.

## 현재 범위

로드맵 `A1`(데스크톱 스파이크, 기획서 7절 P0) + `C3`(토큰 저장) + `S8`(로컬 MCP 서버, 2026-09-09) + `S10`(`show_answer` 도구, 2026-09-09)
+ 트랙 A 배선(`A2`·`A4`·`A7`~`A10`, 2026-09-09 — 모듈은 다른 세션이 만들고 배선·테스트 정정은 사용자 지시 `남은 구현 다 해`)까지다.
**트랙 L(앱 안 구독 탐색)은 `L1`(구독 OAuth 로그인·보관·갱신)까지 구현됐다**(2026-09-09, `src/main/llm/`,
`test/agentResidue.test.ts`로 잔재 검사 범위 조정)·`L2`(앱 안 채팅 호출·서버 SSE 폴백)·`L3`(구독 설정 화면 `/claude-subscription`, frontend)까지 같은 날 구현됐다. 남은 것은 실제 구독으로의 종단 실측(로드맵 U33·U34)뿐이다.
`S3`(탐색 IPC·사용자 LLM 클라이언트)은 2026-09-08 폐기돼 코드가 지워졌다. 트랙 L은 그 코드를
되살리는 것이 아니라 **구독 OAuth 기반으로 새로 쓴 것**이다(자격증명 출처가 다르다).

| 있음 | 없음(담당 작업) |
| --- | --- |
| 원격 오리진 로드, 보안 기본값, Fuses | 서명·공증·DMG·Squirrel·릴리스 (`A3`) |
| 네비게이션·리다이렉트 허용 목록, 새 창은 목록 안만 자식 창(밖은 거부·외부 브라우저. 로그인 뷰는 새 창 자체를 만들지 않는다 — Q69) | 자동 업데이트의 **실효**(`A4` — 정책·접착은 있으나 미서명·미패키징 빌드에서는 꺼진다, 로드맵 Q59) |
| 세션 권한 정책, IPC sender 검증 | 기기 목록·원격 로그아웃 UI (`A11`) |
| 메뉴(로그인·로그아웃·업데이트 확인·CLI 에이전트 연결), 외부 링크, 오프라인 화면, 파일 로그 | crashReporter (`A2` 잔여, 로드맵 Q5 미수집) |
| 액세스 토큰 `safeStorage` 저장(`auth.bin`) + 창 상태 복원(`window-state.json`, `A2`) | 실제 dev 백엔드로의 2단계 로그인 종단(사용자 로그인 필요) |
| **메인 창 안 로그인 뷰·loopback·`knot://auth/callback`·리프레시 갱신·Bearer 주입**(`A7`, `auth-session.bin`. 로그인 화면은 2026-09-10 사용자 지시로 자식 창에서 메인 창 안 `WebContentsView`로 옮겼다 — 창을 새로 만들지 않는다, 로드맵 Q68) | |
| **`knot://` 딥링크**(초대·채팅, 웜·콜드 스타트, `A8`) — SPA 쪽 구독은 `frontend/src/shared/routes/DeepLinkListener` | 패키징 앱에서의 스킴 등록 실측(로드맵 G4) |
| **트레이·글로벌 단축키(`⌘⇧K`)·퀵 질문 창**(`A9`) | |
| **Notion 동기화 완료 알림·Dock 배지**(`A10`) + preload `notifications.show` | |
| 로컬 MCP 서버(`utilityProcess`, `127.0.0.1:47871/mcp`)·연결 토큰(`agent-bridge.json`)·`search_documents`·`list_workspaces` 도구·preload `agent` API (`S8`) | |
| `show_answer` 도구(`S10`) — 에이전트 답변·근거를 세션에 저장하고 창을 앞으로 가져와 `knot:deep-link`로 그 대화를 연다 | 실제 `show_answer` 저장 종단(로컬 백엔드 로그인 뒤 실측) |
| 서버 API 클라이언트(`src/main/chat/knotApi.ts`, Bearer — 워크스페이스 목록·Workspace 검색·세션 생성·턴 저장) | |
| **사용자 Claude 구독 OAuth 로그인·`safeStorage` 보관(`subscription-auth.bin`)·자동 갱신·로그아웃**(`L1`, 2026-09-09) + preload `llm.getStatus/signIn/signOut/onStatusChanged/getSettings/updateSettings` + 메뉴 `Claude 구독 → 구독 설정 열기`(웹 `/claude-subscription`, `L3`) + 설정 `subscription-settings.json`(모델·effort, 로드맵 Q62·Q67) | 실제 claude.ai OAuth 종단(로드맵 U33 — 구독을 가진 사용자가 실측) |
| **앱 안 채팅 호출**(`L2`, 2026-09-09) — preload `llm.streamAnswer` → Workspace 검색(`S7`) → 히스토리 → 구독 Messages API 스트리밍(`src/main/llm/messagesClient.ts`) → 턴 저장(`S2`, `generated_by=CLIENT`). 첫 chunk 전의 구독 쪽 실패는 `fallback: true`로 웹이 서버 SSE에 재전송(로드맵 Q66) | 실제 구독으로 질문 → 저장 → 찾은 문서 표시 종단(U34) |
| Knot 스킬(`resources/skills/knot/SKILL.md`), 세 CLI 등록 스니펫 복사(메뉴 `CLI 에이전트 연결`) | 연결 안내 화면은 웹 SPA `/agent-connection`(`S9`, frontend) |

로컬 MCP 서버는 앱이 켜져 있는 동안 `http://127.0.0.1:47871/mcp`(Streamable HTTP, 로드맵 Q47)에서 듣고,
연결 설정은 `~/Library/Application Support/Knot/agent-bridge.json`(포트·연결 토큰, 0600, 로드맵 Q48)에 둔다.
`Origin` 헤더가 있는 요청과 연결 토큰이 없거나 다른 요청은 거부한다. 서버 액세스 토큰·연결 토큰·질문·검색
결과 본문은 로그와 도구 결과에 남기지 않는다. 폐기된 `S3`가 만들던 `llm-settings.json`·`llm-key.bin`은
앱 시작 시 지운다(트랙 L의 설정 파일은 그래서 `subscription-settings.json`이다).

### CLI 에이전트 연결 (Claude Code 예)

1. 앱 메뉴 **CLI 에이전트 연결 → Claude Code 등록 명령 복사**를 누르고 터미널에 붙여 넣는다
   (`claude mcp add --transport http knot http://127.0.0.1:47871/mcp --header "Authorization: Bearer <연결 토큰>"`).
   Codex CLI는 `~/.codex/config.toml` 스니펫, Gemini CLI는 `gemini mcp add` 명령을 같은 메뉴에서 복사한다.
2. **Knot 스킬 설치 명령 복사**로 `SKILL.md`를 `~/.claude/skills/knot/`·`~/.agents/skills/knot/`에 복사한다.
3. `claude mcp list`에 `knot … ✔ Connected`가 보이면 터미널에서 팀 문서 질문을 한다. 앱에 로그인돼 있지 않으면
   도구가 `UNAUTHENTICATED: Knot 앱에 로그인하세요`를 돌려준다.
4. 답을 앱에서 보고 싶으면 "Knot 앱에서 보여줘"라고 말한다. 에이전트가 `show_answer`로 질문·답변·근거를 저장하면
   앱 창이 앞으로 오고 그 대화가 열린다(웹 SPA는 `DeepLinkListener`가 `knot:deep-link`를 받아 그 세션으로 이동한다 — 2026-09-09, 로드맵 U32).

**연결 토큰 재발급**을 하면 기존 등록은 무효가 되므로 1번을 다시 한다. 2026-09-09 이 PC의 Claude Code 2.1.263으로
등록·도구 호출 왕복까지 실측했다(로드맵 4.5절 `S8` 실측).

## 개발

```bash
pnpm install
pnpm start        # dev 환경(https://dev.knoted.kr)으로 빌드 후 실행
pnpm test         # vitest (main 순수 함수)
pnpm typecheck    # main + preload 두 tsconfig
pnpm make         # 패키징 + zip
```

### 환경

빌드 시점에 상수로 고정한다. 런타임 전환 수단은 두지 않는다(기획서 4.5 — 피싱 표면).

| 변수 | 값 | 비고 |
| --- | --- | --- |
| `KNOT_DESKTOP_ENV` | `prod` \| `dev` \| `local` | 기본 `dev` |
| `KNOT_API_ORIGIN` | API 오리진 | `prod` 빌드에서 **필수**(로드맵 Q3). dev·local은 생략 |

```bash
KNOT_DESKTOP_ENV=local pnpm start                                   # frontend pnpm dev(:3000) + backend bootRun(:8080)과 함께
KNOT_DESKTOP_ENV=prod KNOT_API_ORIGIN=https://... pnpm make         # 운영 빌드
```

`local`은 웹 `http://localhost:3000`·API `http://localhost:8080`으로 **빌드 시점에 고정**된다
(`src/shared/env.ts`). 그래서 세 개를 각각 띄우는 대신 저장소 루트의 한 명령으로 순서대로 띄운다.

```bash
../scripts/dev.sh          # postgres → backend(:8080) → frontend(:3000) → 이 셸(local)
../scripts/dev.sh --help   # --skip-db · --skip-backend · --skip-web · --skip-desktop · --open
```

이미 떠 있는 것은 다시 띄우지 않고 그대로 쓰고(`:8080` 헬스, `:3000` 번들 태그로 판별), Ctrl+C는
그 스크립트가 띄운 것만 정리한다. **앱이 이미 실행 중이면 단일 인스턴스 잠금 때문에 새 창 없이 바로
종료된다** — 먼저 끄거나 `--skip-desktop`으로 서버만 띄운다. 서버 채팅 LLM은 `ANTHROPIC_API_KEY`가
없으면 `fake`로 뜬다(구독 경로 검증에서는 이게 기본 — 폴백이 일어나면 "테스트 LLM 응답입니다"로 바로 구분된다).

### 로그

`~/Library/Logs/Knot/main.log` (메뉴 → 도움말 → 로그 파일 열기). 네비게이션·리다이렉트가
모두 남으므로 OAuth 체인을 확인할 때 쓴다.

## 알아둘 것

- **아키텍처**: Forge는 실행 중인 Node의 `process.arch`로 타깃을 정한다. nvm이 x64
  Node를 쓰고 있으면 Apple Silicon에서도 `darwin-x64` 빌드가 나온다.
  `pnpm exec electron-forge make --arch=arm64`로 명시한다.
- **pnpm**: `pnpm-workspace.yaml`의 `nodeLinker: hoisted`는 Electron·Forge 공식 요구이고,
  `blockExoticSubdeps: false`는 Forge 7.11.2가 `@electron/node-gyp`을 git으로 참조해서
  필요하다. 두 설정 모두 워크스페이스 루트 단위라 `desktop/`을 별도 루트로 둔다.
- **Fuse 검증**: `npx @electron/fuses read --app out/Knot-darwin-arm64/Knot.app`.
- **Playwright E2E**: 프로덕션 Fuse(`EnableNodeCliInspectArguments: false`)가 Playwright
  실행을 막으므로 미패키징 빌드에서 돌린다(기획서 11절). 아직 없다 — `A2`.
