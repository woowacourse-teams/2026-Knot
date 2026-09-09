# Knot 데스크톱 셸

`https://knoted.kr` 웹 앱을 원격 로드하는 Electron 셸이다. 탐색(채팅)의 답변은 사용자가 터미널에서
쓰는 CLI 코딩 에이전트(Claude Code·Codex CLI·Gemini CLI)가 만들고, 이 셸은 그 에이전트가 붙는
**로컬 MCP 서버**를 띄워 문서 검색 도구를 제공한다(기획서 6.4, 2026-09-09 개정). 셸은 LLM을
호출하지 않고 LLM 자격증명을 저장·중개하지 않으며 CLI 바이너리를 실행·변경하지 않는다(로드맵
불변 계약 2·3번). 셸 안의 웹 채팅 UI는 브라우저와 같은 서버 SSE 경로를 쓴다(로드맵 Q22). 셸이
더하는 것은 상시 실행·딥링크·알림·퀵 질문 창·자동 업데이트, 그리고 이 CLI 에이전트 연결이다.

**작업 전에 [실행 정본 로드맵](../docs/electron-desktop-app-roadmap.md)을 먼저 읽는다.**
설계 근거는 [기술 기획서](../docs/electron-desktop-app-tech-plan.md), 조사 사실은
[지식 문서](../docs/electron-desktop-app-knowledge.md)에 있다. 코드와 문서가 어긋나면
문서가 맞다.

## 현재 범위

로드맵 `A1`(데스크톱 스파이크, 기획서 7절 P0) + `C3`(토큰 저장) + `S8`(로컬 MCP 서버, 2026-09-09) + `S10`(`show_answer` 도구, 2026-09-09)
+ 트랙 A 배선(`A2`·`A4`·`A7`~`A10`, 2026-09-09 — 모듈은 다른 세션이 만들고 배선·테스트 정정은 사용자 지시 `남은 구현 다 해`)까지다.
`S3`(탐색 IPC·사용자 LLM 클라이언트)은 구현됐다가 2026-09-08 폐기됐고 `S8`에서 코드를 지웠다. 그 위에
새 기능을 얹지 않는다.

| 있음 | 없음(담당 작업) |
| --- | --- |
| 원격 오리진 로드, 보안 기본값, Fuses | 서명·공증·DMG·Squirrel·릴리스 (`A3`) |
| 네비게이션·리다이렉트 허용 목록, 새 창은 목록 안만 자식 창(밖은 거부·외부 브라우저) | 자동 업데이트의 **실효**(`A4` — 정책·접착은 있으나 미서명·미패키징 빌드에서는 꺼진다, 로드맵 Q59) |
| 세션 권한 정책, IPC sender 검증 | 기기 목록·원격 로그아웃 UI (`A11`) |
| 메뉴(로그인·로그아웃·업데이트 확인·CLI 에이전트 연결), 외부 링크, 오프라인 화면, 파일 로그 | crashReporter (`A2` 잔여, 로드맵 Q5 미수집) |
| 액세스 토큰 `safeStorage` 저장(`auth.bin`) + 창 상태 복원(`window-state.json`, `A2`) | 실제 dev 백엔드로의 2단계 로그인 종단(사용자 로그인 필요) |
| **시스템 브라우저 로그인·loopback·`knot://auth/callback`·리프레시 갱신·Bearer 주입**(`A7`, `auth-session.bin`) | |
| **`knot://` 딥링크**(초대·채팅, 웜·콜드 스타트, `A8`) — SPA 쪽 구독은 `frontend/src/shared/routes/DeepLinkListener` | 패키징 앱에서의 스킴 등록 실측(로드맵 G4) |
| **트레이·글로벌 단축키(`⌘⇧K`)·퀵 질문 창**(`A9`) | |
| **Notion 동기화 완료 알림·Dock 배지**(`A10`) + preload `notifications.show` | |
| 로컬 MCP 서버(`utilityProcess`, `127.0.0.1:47871/mcp`)·연결 토큰(`agent-bridge.json`)·`search_documents`·`list_workspaces` 도구·preload `agent` API (`S8`) | |
| `show_answer` 도구(`S10`) — 에이전트 답변·근거를 세션에 저장하고 창을 앞으로 가져와 `knot:deep-link`로 그 대화를 연다 | 실제 `show_answer` 저장 종단(로컬 백엔드 로그인 뒤 실측) |
| 서버 API 클라이언트(`src/main/chat/knotApi.ts`, Bearer — 워크스페이스 목록·Workspace 검색·세션 생성·턴 저장) | |
| Knot 스킬(`resources/skills/knot/SKILL.md`), 세 CLI 등록 스니펫 복사(메뉴 `CLI 에이전트 연결`) | 연결 안내 화면은 웹 SPA `/agent-connection`(`S9`, frontend) |

로컬 MCP 서버는 앱이 켜져 있는 동안 `http://127.0.0.1:47871/mcp`(Streamable HTTP, 로드맵 Q47)에서 듣고,
연결 설정은 `~/Library/Application Support/Knot/agent-bridge.json`(포트·연결 토큰, 0600, 로드맵 Q48)에 둔다.
`Origin` 헤더가 있는 요청과 연결 토큰이 없거나 다른 요청은 거부한다. 서버 액세스 토큰·연결 토큰·질문·검색
결과 본문은 로그와 도구 결과에 남기지 않는다. 폐기된 `S3`가 만들던 `llm-settings.json`·`llm-key.bin`은
앱 시작 시 지운다.

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
