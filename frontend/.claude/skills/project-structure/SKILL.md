---
name: project-structure
description: 프로젝트 전체 폴더 구조와 레이어(pages / modules / shared)별 역할, 네이밍·운영 원칙 가이드. 새 파일·폴더를 만들거나, 기존 코드를 어디에 둘지·어느 레이어로 옮길지 판단하거나, 훅·로직을 컴포넌트 `model`에 둘지 `shared`로 내릴지 판단하거나, "이 파일 어디에 둬야 해", "폴더 구조 알려줘", "project-structure" 같은 요청을 받을 때 사용.
user-invocable: true
---

# 프로젝트 폴더 구조 가이드

이 스킬은 **파일·폴더의 위치와 이름을 결정**할 때 사용함. 새 파일 생성, 기존 파일 이동, 레이어 판단이 필요한 시점에 먼저 로드.

폴더 구조는 '변경에 유연함'을 판단 근거로 잡았기 때문에, 아래 모든 규칙은 "코드가 어떤 이유로 함께 바뀌는가"를 따라 결정됨.

## 이 스킬이 하는 일

1. **대상 파악** - 만들거나 옮길 대상이 무엇인지(컴포넌트 / 훅 / API / 유틸 / 타입 / 테스트 등) 특정
2. **레이어 결정** - 아래 [레이어별 역할](#레이어별-역할)에 따라 `pages` / `modules` / `shared` 중 어디에 둘지 결정
3. **경로·이름 확정** - [전체 폴더 구조](#전체-폴더-구조)에 맞춰 최종 경로를 확정하고, 폴더·파일명은 `.claude/rules/general-code-convention.md`의 네이밍 규칙을 따름
4. **세부 규칙 위임** - 위치가 정해진 뒤의 내부 구성은 아래 역할별 문서를 따름

## 세부 규칙 문서

위치가 정해진 이후의 세부 규칙은 역할별 문서 참고.

- 폴더·파일 네이밍: `.claude/rules/general-code-convention.md`
- 컴포넌트 위치(추상화 레벨): `.claude/rules/component-abstract-pattern.md`
- 컴포넌트 폴더 구성(콜로케이션): `.claude/rules/component-colocation-pattern.md`
- 세그먼트 정의·배치·의존 규칙: `.claude/rules/segment-pattern.md`
- shared 레이어 구성·폴더별 역할: `.claude/rules/shared-layer.md`
- API 구조: `.claude/rules/api-guide.md` / `.claude/rules/query-hooks.md`
- 훅: `.claude/rules/hook-guide.md`
- 테스트: `.claude/rules/test-strategy.md`

모두 아닐 시 위의 규칙 문서를 사용하지 않기.

## 전체 폴더 구조

```plaintext
src/
├── pages/
│
├── modules/
│   ├── widgets/{domain}/AComponent/
│   │   ├── index.tsx
│   │   ├── ui/
│   │   │   ├── LoadingFallback.tsx
│   │   │   └── ErrorFallback.tsx
│   │   ├── model/
│   │   │   └── use{Domain}.ts
│   │   ├── context/
│   │   │   └── {domain}Context.tsx
│   │   ├── types/
│   │   │   └── {domain}.ts
│   │   └── test.tsx                 # 통합 테스트
│   │
│   └── features/{domain}/BComponent/
│       ├── index.tsx
│       ├── model/
│       │   └── use{Domain}.ts
│       └── utils/
│           ├── formatDate.ts
│           └── formatDate.test.ts   # 단위 테스트
│
├── shared/
│   ├── api/
│   │   ├── httpClient/
│   │   ├── dto/
│   │   │   └── user.ts              # 도메인별 요청·응답 DTO 클래스 + Raw/Input 타입
│   │   ├── fetch/
│   │   │   └── api/v1/users/
│   │   │       ├── index.ts
│   │   │       └── [id]/
│   │   │           └── index.ts
│   │   ├── queryKey/
│   │   │   └── todo.ts
│   │   ├── queries/
│   │   │   └── useTodoQuery/
│   │   │       └── index.ts
│   │   ├── mutations/
│   │   ├── suspense/
│   │   ├── prefetch/
│   │   └── mock/                    # msw mock API (연동 후에도 테스트용으로 유지)
│   │       ├── browser.ts
│   │       ├── server.ts
│   │       ├── handlers/
│   │       │   ├── index.ts
│   │       │   └── api/v1/users/    # fetch/ 와 동일한 경로 구조
│   │       │       └── index.ts
│   │       └── responses/
│   │           └── user.ts          # 도메인별 mock 응답 데이터
│   ├── components/
│   │   ├── primitives/
│   │   │   ├── ui/
│   │   │   │   └── Button/
│   │   │   │       └── index.tsx
│   │   │   ├── layout/
│   │   │   └── animation/
│   │   └── composites/
│   ├── hooks/
│   │   ├── domain/
│   │   └── common/
│   ├── provider/
│   │   ├── context/
│   │   │   └── modalContext/
│   │   │       └── index.tsx
│   │   ├── queryClient/
│   │   └── themeProvider/
│   ├── routes/
│   ├── utils/
│   │   └── formatDate/
│   │       ├── index.ts
│   │       └── test.ts              # 단위 테스트
│   ├── constants/
│   └── types/
│       └── user.ts
│
└── __test__/
    └── dashboardPage.test.ts        # e2e 테스트
```

## 레이어별 역할

각 레이어의 정의와 판단 기준은 아래 문서가 원본이므로 여기서 반복하지 않음.

| 레이어                                                                                              | 정의·판단 기준                                                                       |
| --------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| `pages` / `modules/widgets` / `modules/features`                                                    | `.claude/rules/component-abstract-pattern.md` (컴포넌트 종류 · 참조 규칙 · 주의사항) |
| `shared` (`api` · `components` · `hooks` · `provider` · `routes` · `utils` · `constants` · `types`) | `.claude/rules/shared-layer.md` (최상위 구성 · 폴더별 역할)                          |

`modules`와 `shared`의 경계, 즉 코드를 컴포넌트 `model`에 둘지 `shared`로 내릴지는 아래 [코드를 shared로 내리는 기준](#코드를-shared로-내리는-기준)을 따름.

## 코드를 shared로 내리는 기준

훅·로직을 컴포넌트 폴더의 `model`에 둘지 `shared`로 내릴지는 `src/modules/**` 작업 중에 판단하게 되므로, `src/shared/**`에서만 주입되는 `shared-layer.md`가 아니라 어디서든 호출할 수 있는 이 스킬이 원본. `segment-pattern.md` · `component-colocation-pattern.md` · `hook-guide.md` · `shared-layer.md`는 모두 이 섹션을 가리킴.

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

컨텍스트 작성 방식(`createContext`·`useContext`·`Provider` 한 파일)과 컴포넌트 전용 컨텍스트의 `context` 세그먼트 코로케이션은 `.claude/rules/segment-pattern.md`의 "context 세그먼트" 규칙을, 전역 Provider(`QueryClient`·`ThemeProvider`·전역 컨텍스트)의 `shared/provider` 배치는 `.claude/rules/shared-layer.md`의 "Context / Provider / Routes" 규칙을 따름.

이 스킬에서만 정의하는 내용:

- 라우트는 프로바이더나 config에 넣기 애매하므로 별도의 `shared/routes` 폴더에서 라우트 정의·path 상수·가드·리다이렉트 로직까지 함께 관리.

## 네이밍 규칙

폴더·파일 네이밍(카멜 통일, `src/pages` 라우트 폴더의 `kebab-case` 예외, 폴더 + `index.ts(x)`, 세그먼트 내부 플랫 파일, `types/`·`context/` 파일명)은 `.claude/rules/general-code-convention.md`의 「폴더 및 파일명」이 원본이므로 여기서 반복하지 않음.

## 판단 체크리스트

파일 위치를 확정하기 전에 아래를 확인.

- [ ] 컴포넌트라면 `.claude/rules/component-abstract-pattern.md`의 판단 기준으로 `modules/widgets` · `modules/features` · `shared/components/*` 중 어디인지 정했는가?
- [ ] 훅·로직이라면 「코드를 shared로 내리는 기준」(이름·인자·반환값·이식 테스트)으로 컴포넌트 `model`에 둘지 `shared/hooks`로 내릴지 정했는가?
- [ ] `shared/`라면 `api` / `components` / `hooks` / `provider` / `routes` / `utils` / `constants` / `types` 중 어디인가?
- [ ] mock 데이터라면 컴포넌트가 아니라 `shared/api/mock`에 두었는가? (`.claude/rules/api-guide.md`의 「API mock」)
- [ ] 서버와 주고받는 요청·응답 DTO라면 `shared/types`나 `fetch/` 파일 안이 아니라 `shared/api/dto/{도메인}.ts`에 클래스(+ 생성자 입력 `Raw`/`Input` 인터페이스)로 두었는가? 같은 도메인은 한 파일. `new`는 `fetch/`·`mutations/`에서만. (`.claude/rules/dto-guide.md`)
- [ ] 폴더·파일명이 `.claude/rules/general-code-convention.md`의 네이밍 규칙(카멜 통일 · 폴더 + `index.ts(x)` · 예외)에 맞는가?
- [ ] 컨텍스트가 특정 컴포넌트 전용이면 해당 컴포넌트의 `context` 세그먼트에, 전역이면 `shared/provider`에 두었는가?
