---
paths:
  - "src/**"
description: 프로젝트 전체 폴더 구조와 레이어별 역할, 네이밍·운영 원칙 가이드라인. 새 파일·폴더 생성이나 파일 위치 판단 전 필독.
---

# 프로젝트 폴더 구조 가이드라인

폴더 구조는 '변경에 유연함'을 판단 근거로 잡았기 때문에, 아래 모든 규칙은 "코드가 어떤 이유로 함께 바뀌는가"를 따라 결정됨.

세부 규칙은 역할별 문서 참고.

- 컴포넌트 위치(추상화 레벨): `.claude/rules/component-abstract-pattern.md`
- 컴포넌트 폴더 구성(콜로케이션): `.claude/rules/component-colocation-pattern.md`
- 세그먼트 정의·배치·의존 규칙: `.claude/rules/segment-pattern.md`
- API 구조: `.claude/rules/api-guide.md` / `.claude/rules/query-hooks.md`
- 훅: `.claude/rules/hook-guide.md`
- 테스트: `.claude/rules/test-strategy.md`

## 전체 폴더 구조

```
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
│   │   └── prefetch/
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

### pages

라우트에 대응하는 화면 단위. widgets를 import해 **배치하는 레이아웃 역할만** 담당하며, 도메인 로직·데이터 패칭은 갖지 않음. UI 단위가 크지 않은 경우(예: 로그인 컴포넌트)에는 features 컴포넌트를 page에서 바로 import 가능.

### modules/widgets

`<section />`으로 분류할 수 있을 만큼 규모가 큰, 문서의 독립적인 구획을 담당. 페이지에서 import되어 사용되며 여러 page에서 재사용 가능, 섹션 단위의 유저 플로우 전체를 책임짐. 하위 요소에 책임을 할당하는 지휘자 역할.

### modules/features

widgets 내부에서 독립적으로 존재할 수 있는, 섹션이 되지 못하는 작은 단위를 담당. 카카오 로그인 버튼처럼 여러 widgets에 들어가 재사용될 수 있으며, 할당받은 완결된 작업을 스스로 수행.

### shared

특정 도메인 컴포넌트에 강결합되지 않아 프로젝트 어디서든 쓸 수 있는 코드를 모아둠. `api`, `components`(primitives/composites), `hooks`, `provider`, `routes`, `utils`, `constants`, `types`로 구성.

## Context / Provider / Routes

- `createContext`, `useContext`, `Provider` 세 가지는 파편화를 막기 위해 한 파일 안에 함께 작성.
- 전역으로 쓰이는 QueryClient, ThemeProvider(이모션 디자인 토큰 설정 포함), 전역 컨텍스트는 `shared/provider`에서 관리.
- 특정 컴포넌트에서만 쓰이는 컨텍스트(컴파운드 패턴, 폼, prop drilling 제거용)는 shared가 아니라 해당 컴포넌트 폴더의 `context` 세그먼트에 코로케이션. (`context/{이름}Context.tsx`)
- 라우트는 프로바이더나 config에 넣기 애매하므로 별도의 `shared/routes` 폴더에서 라우트 정의·path 상수·가드·리다이렉트 로직까지 함께 관리.

## 네이밍 규칙

- 폴더명은 구현체 이름으로 짓고, 구현체 파일은 `index.ts(x)`로 통일. (예: `Button/index.tsx`) 코로케이션과 함께 변경에 유연함을 열어두기 위한 선택.
- 케밥 케이스와 카멜 케이스가 섞여 헷갈리는 문제가 있어, 네이밍은 카멜(파스칼 포함)로 통일.
- **예외: `src/pages` 하위의 라우트 폴더는 `kebab-case` 허용.** 폴더 경로가 곧 URL 경로이므로 URL 표기를 그대로 따름. (e.g. `pages/join-error` → `/join-error`) 레이아웃·동적 세그먼트 등 라우팅 규약용 특수 폴더(`_layout`, `[workspaceId]`)도 규약 표기를 그대로 사용.
- 위 예외를 제외한 나머지(`modules`, `shared` 전체)에서는 케밥 케이스를 사용하지 않음.
- 인덱스 파일과 어색하게 섞이지 않도록, 인덱스를 제외한 나머지는 폴더로 둠. 테스트 파일(`test.ts`, 컴포넌트 통합 테스트는 `test.tsx`)은 예외.
- **컴포넌트 폴더의 세그먼트(`ui`/`model`/`utils`/`types`/`constants`/`context`) 내부는 예외**: 폴더 + `index.ts`를 다시 쓰지 않고 구현체 이름의 플랫 파일로 둠. (예: `ui/LoadingFallback.tsx`, `model/useCalendar.ts`, `utils/formatDate.ts` + `utils/formatDate.test.ts`) `shared` 레이어는 기존대로 폴더 + `index.ts`.
- 타입 파일은 `index.ts`를 두지 않고 항상 `types/` 폴더 안에 `user.ts`처럼 내용을 나타내는 이름으로 작성. (`types.ts` 단일 파일은 쓰지 않음)
- 컴포넌트 세그먼트의 `context`는 Provider(JSX)를 함께 작성하므로 `context/{이름}Context.tsx`처럼 `.tsx`로 둠. (`shared/provider/context`는 기존대로 폴더 + `index.tsx`)
