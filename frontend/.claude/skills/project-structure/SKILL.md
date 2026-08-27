---
name: project-structure
description: 프로젝트 전체 폴더 구조와 레이어(pages / modules / shared)별 역할, 네이밍·운영 원칙 가이드. 새 파일·폴더를 만들거나, 기존 코드를 어디에 둘지·어느 레이어로 옮길지 판단하거나, "이 파일 어디에 둬야 해", "폴더 구조 알려줘", "project-structure" 같은 요청을 받을 때 사용.
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
- shared 레이어 구성·shared로 내리는 기준: `.claude/rules/shared-layer.md`
- API 구조: `.claude/rules/api-guide.md` / `.claude/rules/query-hooks.md`
- 훅: `.claude/rules/hook-guide.md`
- 테스트: `.claude/rules/test-strategy.md`

모두 아닐 시 위의 규칙 문서를 사용하지 않기.

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

각 레이어의 정의와 판단 기준은 아래 문서가 원본이므로 여기서 반복하지 않음.

| 레이어                                                                                              | 정의·판단 기준                                                                       |
| --------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| `pages` / `modules/widgets` / `modules/features`                                                    | `.claude/rules/component-abstract-pattern.md` (컴포넌트 종류 · 참조 규칙 · 주의사항) |
| `shared` (`api` · `components` · `hooks` · `provider` · `routes` · `utils` · `constants` · `types`) | `.claude/rules/shared-layer.md` (최상위 구성 · 폴더별 역할 · shared로 내리는 기준)   |

## Context / Provider / Routes

컨텍스트 작성 방식(`createContext`·`useContext`·`Provider` 한 파일)과 컴포넌트 전용 컨텍스트의 `context` 세그먼트 코로케이션은 `.claude/rules/segment-pattern.md`의 "context 세그먼트" 규칙을, 전역 Provider(`QueryClient`·`ThemeProvider`·전역 컨텍스트)의 `shared/provider` 배치는 `.claude/rules/shared-layer.md`의 "Context / Provider / Routes" 규칙을 따름.

이 스킬에서만 정의하는 내용:

- 라우트는 프로바이더나 config에 넣기 애매하므로 별도의 `shared/routes` 폴더에서 라우트 정의·path 상수·가드·리다이렉트 로직까지 함께 관리.

## 네이밍 규칙

폴더·파일 네이밍(카멜 통일, `src/pages` 라우트 폴더의 `kebab-case` 예외, 폴더 + `index.ts(x)`, 세그먼트 내부 플랫 파일, `types/`·`context/` 파일명)은 `.claude/rules/general-code-convention.md`의 「폴더 및 파일명」이 원본이므로 여기서 반복하지 않음.

## 판단 체크리스트

파일 위치를 확정하기 전에 아래를 확인.

- [ ] 컴포넌트라면 `.claude/rules/component-abstract-pattern.md`의 판단 기준으로 `modules/widgets` · `modules/features` · `shared/components/*` 중 어디인지 정했는가?
- [ ] `shared/`라면 `api` / `components` / `hooks` / `provider` / `routes` / `utils` / `constants` / `types` 중 어디인가?
- [ ] 폴더·파일명이 `.claude/rules/general-code-convention.md`의 네이밍 규칙(카멜 통일 · 폴더 + `index.ts(x)` · 예외)에 맞는가?
- [ ] 컨텍스트가 특정 컴포넌트 전용이면 해당 컴포넌트의 `context` 세그먼트에, 전역이면 `shared/provider`에 두었는가?
