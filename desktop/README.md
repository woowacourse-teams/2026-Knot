# Knot 데스크톱 셸

`https://knoted.kr` 웹 앱을 원격 로드하는 Electron 셸이다. 채팅·검색·LLM 호출·답변
저장은 기존 백엔드 계약을 그대로 쓴다. 셸이 더하는 것은 상시 실행·딥링크·알림·퀵 질문
창·자동 업데이트다.

**작업 전에 [실행 정본 로드맵](../docs/electron-desktop-app-roadmap.md)을 먼저 읽는다.**
설계 근거는 [기술 기획서](../docs/electron-desktop-app-tech-plan.md), 조사 사실은
[지식 문서](../docs/electron-desktop-app-knowledge.md)에 있다. 코드와 문서가 어긋나면
문서가 맞다.

## 현재 범위

로드맵 `A1`(데스크톱 스파이크, 기획서 7절 P0)까지다.

| 있음 | 없음(담당 작업) |
| --- | --- |
| 원격 오리진 로드, 보안 기본값, Fuses | 자동 업데이트 (`A4`) |
| 네비게이션·리다이렉트 허용 목록, 새 창 거부 | `knot://` 딥링크 (`A8`) |
| 세션 권한 정책, IPC sender 검증 | 트레이·퀵 질문 창 (`A9`) |
| 메뉴, 외부 링크, 오프라인 화면, 파일 로그 | 디바이스 토큰 인증 (`A6`·`A7`) |
| 미서명(ad-hoc) 로컬 빌드 | 서명·공증·DMG·Squirrel·릴리스 (`A3`) |
| | 창 상태 복원, crashReporter (`A2`) |

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
KNOT_DESKTOP_ENV=local pnpm start                                   # frontend pnpm dev와 함께
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
