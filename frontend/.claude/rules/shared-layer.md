---
paths:
  - "src/shared/**"
description: shared 레이어의 최상위 구성(api / components / hooks / provider / routes / utils / constants / types)과 각 폴더의 역할, 네이밍·의존 규칙 가이드라인. shared에 코드 생성·배치·이동 전 필독.
---

# shared 레이어 가이드라인

**`shared`는 특정 도메인 컴포넌트에 강결합되지 않아 프로젝트 어디서든 쓸 수 있는 코드를 모아두는 레이어.** 이 문서는 shared의 최상위 구성과 각 폴더의 역할을 정의함. 코드를 shared로 내릴지 판단하는 세부 기준은 `project-structure` 스킬 참고.

폴더 구조는 '변경에 유연함'을 판단 근거로 잡았기 때문에, 아래 모든 규칙은 "코드가 어떤 이유로 함께 바뀌는가"를 따라 결정됨.

## 세그먼트와의 구분

`shared` 최상위 폴더 구성은 shared 레이어 자체의 구성이며, 컴포넌트 폴더 내부를 역할별로 나누는 세그먼트(`ui` / `model` / `utils` / `types` / `constants` / `context`)와는 별개 개념. `utils`·`types`·`constants`처럼 이름이 겹치는 폴더가 있어도 별개.

- 컴포넌트 세그먼트의 내용물 → 해당 컴포넌트 내부 전용
- `shared`의 폴더 → 프로젝트 전역 공용

세그먼트 규칙은 `.claude/rules/segment-pattern.md` 참고.

## 최상위 구성

| 폴더         | 역할                                                                                                                                                                                                                                     | 세부 규칙                                                                                                                          |
| ------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `api`        | `httpClient` · 요청·응답 DTO 클래스(`dto`) · 요청 함수(`fetch`) · 쿼리 키(`queryKey`) · 쿼리/뮤테이션 훅(`queries` / `mutations`) · `suspense` · `prefetch` · mock API(`mock`). API 코드는 컴포넌트 폴더가 아니라 여기서 계층적으로 관리 | `.claude/rules/api-guide.md`, `.claude/rules/dto-guide.md`, `.claude/rules/query-hooks.md`                                         |
| `components` | 도메인 로직을 다루지 않는 공통 컴포넌트. `composites`(ui 로직 포함) / `primitives`(ui만, `ui` · `layout` · `animation`으로 분류). 컴포넌트 폴더 내부는 세그먼트 규칙을 따름                                                              | `.claude/rules/component-abstract-pattern.md`, `.claude/rules/component-colocation-pattern.md`, `.claude/rules/segment-pattern.md` |
| `hooks`      | 특정 컴포넌트에 강결합되지 않은 훅. `domain`(도메인 로직 O, 도메인별 디렉토리) / `common`(도메인 로직 X). 쿼리·뮤테이션 훅은 여기가 아니라 `api`에서 관리                                                                                | `.claude/rules/hook-guide.md`                                                                                                      |
| `provider`   | 전역 QueryClient(`queryClient`) · ThemeProvider(디자인 토큰, `themeProvider`) · 전역 컨텍스트(`context/{이름}Context/index.tsx`)                                                                                                         | —                                                                                                                                  |
| `routes`     | 라우트 정의 · path 상수 · 가드 · 리다이렉트 로직                                                                                                                                                                                         | —                                                                                                                                  |
| `utils`      | 전역 공용 유틸 함수. 폴더 + `index.ts`, 단위 테스트는 같은 폴더의 `test.ts`                                                                                                                                                              | `.claude/rules/test-strategy.md`                                                                                                   |
| `constants`  | 전역 공용 상수                                                                                                                                                                                                                           | —                                                                                                                                  |
| `types`      | 전역 공용 타입. `types/user.ts`처럼 내용을 나타내는 이름으로 분리. 서버와 주고받는 요청·응답 DTO(클래스와 그 `Raw`·`Input` 타입)는 여기가 아니라 `api/dto`                                                                               | —                                                                                                                                  |

## 네이밍

- `shared`는 폴더 + `index.ts(x)` 코로케이션을 그대로 씀. (예: `utils/formatDate/index.ts`, `hooks/common/useHaptic/index.ts`, `provider/context/modalContext/index.tsx`) 세그먼트 내부의 플랫 파일 규칙은 shared에 적용하지 않음.
- 단위 테스트는 구현 폴더 안에 `test.ts`로 둠. (예: `utils/formatDate/index.ts` + `utils/formatDate/test.ts`)
- 타입은 `types/` 폴더 안에 `types/user.ts`처럼 내용을 나타내는 이름으로 분리. (`types.ts` 단일 파일 · `types/index.ts`는 쓰지 않음)
- 네이밍은 카멜(파스칼 포함)로 통일. 케밥 케이스는 쓰지 않음.

## 코드를 shared/ 로 내리는 기준

`shared`에 둘지 컴포넌트 폴더(세그먼트)에 둘지는 사용 횟수("지금은 여기서만 쓴다")가 아니라 **컴포넌트와의 강결합 여부**로 판단. 컴포넌트와 강결합된 훅은 도메인 폴더(modules)의 `model`에 두고, 아니면 `shared/hooks`에 둠. 강결합된 로직이더라도 도메인과 무관한 UI 로직이면 `shared/components/composites/{Component}/model`에 둠.

세부 판단 기준(이름·인자·반환값·다른 화면 이식 테스트)은 `src/modules/**` 작업 중에도 필요하므로 `src/shared/**`에서만 주입되는 이 문서가 아니라 `project-structure` 스킬의 「코드를 shared로 내리는 기준」이 원본. 판단이 필요하면 해당 스킬을 호출.

## Context / Provider / Routes

- 전역 컨텍스트 · QueryClient · ThemeProvider(디자인 토큰)는 `shared/provider`에서 관리. 전역 컨텍스트는 `provider/context/{이름}Context/index.tsx`.
- `createContext`, `useContext`, `Provider` 세 가지는 파편화를 막기 위해 한 파일 안에 함께 작성.
- 특정 컴포넌트에서만 쓰이는 컨텍스트(컴파운드 패턴, 폼, prop drilling 제거용)는 `shared/provider/context`에 두지 않고 해당 컴포넌트 폴더의 `context` 세그먼트에 코로케이션. (`.claude/rules/segment-pattern.md` 참고)
- 라우트는 프로바이더나 config에 넣기 애매하므로 `shared/routes`에서 라우트 정의 · path 상수 · 가드 · 리다이렉트 로직까지 함께 관리.

## 의존 규칙

- `shared`의 코드는 특정 도메인 컴포넌트에 강결합되지 않아 프로젝트 어디서든 쓸 수 있어야 함.
- 도메인이 겹치는 상황에서는 컴포넌트를 재사용하지 않고, 도메인 로직을 훅으로 만들어 `shared/hooks/domain`으로 내려서 재사용.
- 특정 컴포넌트 전용 컨텍스트를 `shared/provider/context`에 두지 않음.
- 쿼리·뮤테이션 훅은 `shared/hooks`가 아니라 `shared/api`의 `queries` / `mutations`에서 관리. (`.claude/rules/query-hooks.md` 참고)
- mock 데이터는 컴포넌트가 아니라 `shared/api/mock`에서 msw 핸들러로 관리하고, 프로덕션 코드는 `mock/`을 import하지 않음. `mock/`은 실제 API 연동 후에도 테스트용으로 유지. (`.claude/rules/api-guide.md` 참고)
- 서버와 주고받는 요청·응답 DTO는 `shared/types`가 아니라 `shared/api/dto`에 도메인별 파일의 **클래스**로 두고, `shared/api` 밖에서는 import하지도 `new`하지도 않음. 컴포넌트·훅은 쿼리 훅의 반환값과 뮤테이션 훅의 `mutate` 인자 추론으로 타입을 받음. (`.claude/rules/dto-guide.md` 참고)
