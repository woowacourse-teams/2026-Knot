---
paths:
  - "src/shared/**"
description: shared 레이어의 최상위 구성(api / components / hooks / provider / routes / utils / constants / types)과 각 폴더의 역할, 코드를 shared로 내리는 판단 기준(컴포넌트 강결합 여부) 가이드라인. shared에 코드 생성·배치·이동 전 필독.
---

# shared 레이어 가이드라인

**`shared`는 특정 도메인 컴포넌트에 강결합되지 않아 프로젝트 어디서든 쓸 수 있는 코드를 모아두는 레이어.** 이 문서는 shared의 최상위 구성과 각 폴더의 역할, 코드를 shared로 내릴지 판단하는 기준을 정의함.

폴더 구조는 '변경에 유연함'을 판단 근거로 잡았기 때문에, 아래 모든 규칙은 "코드가 어떤 이유로 함께 바뀌는가"를 따라 결정됨.

## 세그먼트와의 구분

`shared` 최상위 폴더 구성은 shared 레이어 자체의 구성이며, 컴포넌트 폴더 내부를 역할별로 나누는 세그먼트(`ui` / `model` / `utils` / `types` / `constants` / `context`)와는 별개 개념. `utils`·`types`·`constants`처럼 이름이 겹치는 폴더가 있어도 별개.

- 컴포넌트 세그먼트의 내용물 → 해당 컴포넌트 내부 전용
- `shared`의 폴더 → 프로젝트 전역 공용

세그먼트 규칙은 `.claude/rules/segment-pattern.md` 참고.

## 최상위 구성

| 폴더         | 역할                                                                                                                                                                                    | 세부 규칙                                                                                                                          |
| ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `api`        | `httpClient` · 요청 함수(`fetch`) · 쿼리 키(`queryKey`) · 쿼리/뮤테이션 훅(`queries` / `mutations`) · `suspense` · `prefetch`. API 코드는 컴포넌트 폴더가 아니라 여기서 계층적으로 관리 | `.claude/rules/api-guide.md`, `.claude/rules/query-hooks.md`                                                                       |
| `components` | 도메인 로직을 다루지 않는 공통 컴포넌트. `composites`(ui 로직 포함) / `primitives`(ui만, `ui` · `layout` · `animation`으로 분류). 컴포넌트 폴더 내부는 세그먼트 규칙을 따름             | `.claude/rules/component-abstract-pattern.md`, `.claude/rules/component-colocation-pattern.md`, `.claude/rules/segment-pattern.md` |
| `hooks`      | 특정 컴포넌트에 강결합되지 않은 훅. `domain`(도메인 로직 O, 도메인별 디렉토리) / `common`(도메인 로직 X). 쿼리·뮤테이션 훅은 여기가 아니라 `api`에서 관리                               | `.claude/rules/hook-guide.md`                                                                                                      |
| `provider`   | 전역 QueryClient(`queryClient`) · ThemeProvider(디자인 토큰, `themeProvider`) · 전역 컨텍스트(`context/{이름}Context/index.tsx`)                                                        | —                                                                                                                                  |
| `routes`     | 라우트 정의 · path 상수 · 가드 · 리다이렉트 로직                                                                                                                                        | —                                                                                                                                  |
| `utils`      | 전역 공용 유틸 함수. 폴더 + `index.ts`, 단위 테스트는 같은 폴더의 `test.ts`                                                                                                             | `.claude/rules/test-strategy.md`                                                                                                   |
| `constants`  | 전역 공용 상수                                                                                                                                                                          | —                                                                                                                                  |
| `types`      | 전역 공용 타입. `types/user.ts`처럼 내용을 나타내는 이름으로 분리                                                                                                                       | —                                                                                                                                  |

## 네이밍

- `shared`는 폴더 + `index.ts(x)` 코로케이션을 그대로 씀. (예: `utils/formatDate/index.ts`, `hooks/common/useHaptic/index.ts`, `provider/context/modalContext/index.tsx`) 세그먼트 내부의 플랫 파일 규칙은 shared에 적용하지 않음.
- 단위 테스트는 구현 폴더 안에 `test.ts`로 둠. (예: `utils/formatDate/index.ts` + `utils/formatDate/test.ts`)
- 타입은 `types/` 폴더 안에 `types/user.ts`처럼 내용을 나타내는 이름으로 분리. (`types.ts` 단일 파일 · `types/index.ts`는 쓰지 않음)
- 네이밍은 카멜(파스칼 포함)로 통일. 케밥 케이스는 쓰지 않음.

## 코드를 shared/ 로 내리는 기준

`shared`에 둘지 컴포넌트 폴더(세그먼트)에 둘지는 사용 횟수("지금은 여기서만 쓴다")가 아니라 **컴포넌트와의 강결합 여부**로 판단. 사용 횟수로 분류하면 강결합된 훅과, 강결합되지 않았지만 한 번밖에 사용되지 않은 훅이 같은 위치에 섞여 재사용 이점을 살릴 수 없기 때문. 이 기준 덕분에 재사용 가능한 훅은 `shared/hooks`만 확인하면 됨.

> **"훅이 컴포넌트와 강결합되어 있다"의 정의**
> 훅이 바뀔 수 있는 원인이 컴포넌트에 있어서, 컴포넌트 UI가 바뀔 때 훅도 같이 바뀔 확률이 매우 높은 상태.

컴포넌트와 강결합된 훅은 도메인 폴더(modules)의 `model`에 두고, 아니면 `shared/hooks`에 둠. 강결합된 로직이더라도 도메인과 무관한 UI 로직이면 `shared/components/composites/{Component}/model`에 둠.

### 기준 1. 이름에서 컴포넌트 이름을 지울 수 있는가

컴포넌트 이름은 암묵적인 입력이므로, 이름에서 컴포넌트를 빼면 무슨 훅인지 알 수 없어진다면 그 컴포넌트만의 요소로 보고 `model`에 둠.

```ts
useCourseDetailBottomSheetState(); // ❌ 이름에 컴포넌트가 박혀 있음 → model
useGetCoursePath(courseId); // ✅ 컴포넌트와 무관한 이름 → shared/hooks/domain/course
```

### 기준 2. 인자가 도메인 값인가, 컴포넌트의 내부 값인가

인자로 ref, setState, 해당 컴포넌트의 로컬 상태를 받는 순간 다른 곳으로 이식 불가이므로 `model`, 도메인 내 어떤 값이든 넘길 수 있다면 `shared`.

```ts
useSheetDrag(sheetRef, setOpen, contentHeight); // ❌ 컴포넌트의 ref/setter를 받음 → 컴포넌트 전용
useDrawMarkers(map, markers); // ✅ 도메인 값만 받음 → 어디서든 호출 가능
```

### 기준 3. 반환값이 도메인 개념인가, 특정 JSX용 props 묶음인가

`headerProps`, `listProps`처럼 특정 컴포넌트의 렌더 구조를 전제한 props 묶음을 반환하면 `model`, `path`나 `isLoading` 같은 보편적인 값만 반환하면 `shared`.

```ts
const { headerProps, listProps, footerProps } = useCourseDetailSheet(); // ❌
const { path, isLoading } = useGetCoursePath(courseId); // ✅
```

### 기준 4. 다른 화면에 붙여보는 상상 테스트

- `shared/hooks/domain/map/useDrawPath`처럼 지도가 있는 화면이면 어디서든 말이 되는 훅은 `shared`.
- `Tabs/model/useTabContext`처럼 특정 Context 안에서만 값이 존재해서 밖에서 부르면 에러이거나 무의미한 훅은 `model`에 코로케이션.

컴포넌트의 렌더 구조·Context·로컬 state에 구조적으로 묶여 있으면 `model`, 도메인 값만 주고받으면 `shared`로 분류. "지금은 여기서만 쓴다"는 판단 근거로 삼지 않음.

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
