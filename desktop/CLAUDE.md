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
- 클라이언트에서 LLM을 호출하지 않는다. 데스크톱 전용 채팅 경로를 만들지 않는다.
- Fuse 7종(`forge.config.ts`)을 낮추지 않는다.

## 구조

| 위치 | 책임 |
| --- | --- |
| `src/main/` | 창·메뉴·네비게이션 정책·IPC·로그. Node 환경 |
| `src/preload/` | `contextBridge`로 `window.knotDesktop` 노출. **CJS 단일 번들**(sandbox preload는 ESM 불가) |
| `src/shared/api.ts` | preload 계약. 웹 SPA가 **복사**해 쓰므로 다른 파일을 import 하지 않는다 |
| `src/shared/env.ts` | 오리진 표(기획서 4.5). 허용 목록의 근거 |
| `test/` | vitest. main의 순수 함수만 — `electron`은 `vi.mock`한다 |

`renderer/`는 없다. 원격 로드이므로 renderer 빌드가 없고 `resources/offline.html`만 로컬이다.

## 코드

- 환경 값은 빌드 시 상수(`__KNOT_ENV__` 등, `src/shared/build-globals.d.ts`)로 받는다.
  런타임 `process.env` 조회로 바꾸지 않는다.
- `electron-log`의 `log.initialize()`를 호출하지 않는다. renderer가 원격 웹사이트라
  사이트 XSS에 로그 쓰기 능력을 주게 된다.
- 새 의존성은 기획서 9.2의 목록을 따른다. `keytar`와 클라이언트 LLM SDK는 금지다.
- 주석·커밋·문서는 한국어로 쓴다. 따옴표는 큰따옴표, 들여쓰기 2칸(`.prettierrc`).

## 검증

`pnpm typecheck && pnpm test`를 통과시킨 뒤 PR을 올린다. 보안에 닿는 변경은
기획서 8절 체크리스트 20항목을 PR 본문에서 확인하고, 패키징 변경은
`npx @electron/fuses read`로 Fuse를 재확인한다.
