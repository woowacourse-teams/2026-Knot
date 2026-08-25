---
paths:
  - "src/modules/**"
  - "src/shared/components/**"
  - "src/shared/hooks/**"
description: 컴포넌트 폴더(widgets·features·composites) 내부 세그먼트(ui / model / utils / types / constants / context)의 정의와 배치·의존 규칙 가이드라인. 세그먼트 코드 생성·배치·이동 전 필독.
---

# 세그먼트 패턴 가이드라인

**세그먼트란 컴포넌트 폴더(widgets·features·composites) 내부를 역할별로 나누는 하위 폴더 구획**. 이 문서는 그 세그먼트의 정의와 배치·의존 규칙을 정의함.

`shared` 최상위 폴더 구성(`api`, `components`, `hooks`, `provider`, `routes`, `utils`, `constants`, `types`)은 세그먼트가 아니라 shared 레이어 자체의 구성. `utils`·`types`·`constants`처럼 이름이 겹치는 폴더가 있어도 별개 개념.
컴포넌트 세그먼트의 내용물은 해당 컴포넌트 내부 전용, `shared`의 폴더는 프로젝트 전역 공용.

폴더 구조는 '변경에 유연함'을 판단 근거로 잡았기 때문에, 아래 모든 규칙은 "코드가 어떤 이유로 함께 바뀌는가"를 따라 결정됨.

## 세그먼트 정의

features, widgets, composites 레이어의 세그먼트는 `ui`, `model`, `utils`, `types`, `constants`로 통일하며, 필요 시 `context`를 선택적으로 추가. `api`는 세그먼트가 아니며, API 관련 코드는 컴포넌트 폴더가 아니라 `shared/api`에서 관리.

`primitives`와 `pages`는 세그먼트를 강제하지 않음. 다만 필요해지면 동일한 세그먼트 규칙을 그대로 적용.

세그먼트는 항상 폴더로 두되, **세그먼트 내부에서는 폴더 + `index.ts` 코로케이션을 다시 쓰지 않고 구현체 이름의 플랫 파일로 둠**. (예: `utils/formatDate.ts` ✅ / `utils/formatDate/index.ts` ❌)

- `ui` : 서브 컴포넌트. `ui/LoadingFallback.tsx`처럼 플랫 파일로 둠.
- `model` : 해당 컴포넌트에 강결합된 훅·상태 로직. `model/useCustomerCenter.ts`처럼 플랫 파일로 둠. (`hooks` 폴더는 `shared` 전용이므로 컴포넌트 폴더에는 만들지 않음)
- `utils` : 유틸 함수. `utils/formatDate.ts`와 `utils/formatDate.test.ts`(단위 테스트)를 나란히 코로케이션.
- `types` : 타입. 항상 `types/` 폴더로 두고, `types/user.ts`처럼 내용을 나타내는 이름의 파일로 나눔. (`types.ts` 단일 파일·`types/index.ts`는 쓰지 않음)
- `constants` : 상수. `constants/errorMessages.ts`처럼 플랫 파일로 둠.
- `context` (선택) : 특정 컴포넌트 전용 컨텍스트. Provider(JSX)를 함께 작성하므로 `context/todoContext.tsx`처럼 `.tsx` 플랫 파일로 둠.

## 훅 위치 판단 기준 (`modules/**/model` vs `shared/hooks`)

훅의 위치는 사용 횟수("지금은 여기서만 쓴다")가 아니라 **컴포넌트와의 강결합 여부**로 판단. 사용 횟수로 분류하면 강결합된 훅과, 강결합되지 않았지만 한 번밖에 사용되지 않은 훅이 같은 위치에 섞여 재사용 이점을 살릴 수 없기 때문. 이 기준 덕분에 재사용 가능한 훅은 `shared/hooks`만 확인하면 됨.

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

## api 코드의 위치

`api`는 세그먼트가 아니므로 컴포넌트 폴더에 두지 않음. 요청 함수·쿼리 키·쿼리/뮤테이션 훅은 모두 `shared/api`에서 계층적으로 관리하며, 내부 구조와 작성 규칙은 `.claude/rules/api-guide.md`, `.claude/rules/query-hooks.md` 참고.

## context 세그먼트

- `createContext`, `useContext`, `Provider` 세 가지는 파편화를 막기 위해 한 파일 안에 함께 작성. Provider가 JSX를 반환하므로 `context/todoContext.tsx`처럼 `.tsx` 파일로 둠.
- 특정 컴포넌트에서만 쓰이는 컨텍스트(컴파운드 패턴, 폼, prop drilling 제거용)는 shared가 아니라 해당 컴포넌트 폴더의 `context` 세그먼트에 코로케이션.
- 전역 컨텍스트·QueryClient·ThemeProvider는 세그먼트가 아니라 `shared/provider`에서 관리. 라우트는 `shared/routes`에서 관리. (`.claude/rules/project-structure.md` 참고)

## 테스트 위치

UI 테스트와 스토리북은 일단 보류, 단위·통합·E2E 세 가지만 가져감.

| 종류        | 위치                                                                | 대상                                    |
| ----------- | ------------------------------------------------------------------- | --------------------------------------- |
| 단위 테스트 | `utils` 세그먼트 안에 구현 파일과 `*.test.ts`를 나란히 코로케이션 (예: `utils/formatDate.ts` + `utils/formatDate.test.ts`) | 유틸 함수 (훅은 테스트하지 않음)        |
| 통합 테스트 | 최종 책임 컴포넌트(대부분 섹션 단위의 widgets) 폴더 안의 `test.tsx` | 패칭부터 UI까지 하나의 유저 플로우 전체 |
| E2E 테스트  | 전역 `__test__/`에 페이지 이름을 붙인 파일(`dashboardPage.test.ts`) | 페이지 단위 수행 권장                   |

## 의존 규칙

- 컴포넌트 폴더의 세그먼트 내용물은 해당 컴포넌트 내부에서만 사용. 외부에 공개하는 것은 `index.tsx`뿐. (`.claude/rules/component-colocation-pattern.md` 참고)
- 도메인이 겹치는 상황에서는 컴포넌트를 재사용하지 않고, 도메인 로직을 훅으로 만들어 `shared/hooks/domain`으로 내려서 재사용.
- 특정 컴포넌트 전용 컨텍스트를 `shared/provider/context`에 두지 않음.
- `shared`의 코드는 특정 도메인 컴포넌트에 강결합되지 않아 프로젝트 어디서든 쓸 수 있어야 함.
