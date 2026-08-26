---
description: 코드 작성 시 지켜야 하는 전역 규칙(네이밍, 타입, 컴포넌트/훅 작성 방식) 가이드라인
---

## 네이밍

### 폴더 및 파일명

- 폴더명: `camelCase`
  - **예외: `src/pages` 하위의 라우트 폴더는 URL 경로를 그대로 반영하므로 `kebab-case` 허용** (e.g. `pages/join-error`, `pages/workspace/code`). 라우팅 규약용 특수 폴더(`_layout`, `[workspaceId]` 등)도 그대로 사용.
- 파일명:
  - 컴포넌트 파일: `PascalCase` + `.tsx`(`.jsx`)
  - 컴포넌트 외 파일: `camelCase` + `.ts`(`.js`)
- 인덱스(`index.ts(x)`)를 제외한 나머지는 폴더 + `index.ts` 형태로 두되, 테스트 파일(`test.ts`, 컴포넌트 통합 테스트는 `test.tsx`)은 예외로 일반 파일로 둠
- **컴포넌트 폴더의 세그먼트(`ui`/`model`/`utils`/`types`/`constants`/`context`) 내부는 예외**: 폴더 + `index.ts`를 다시 쓰지 않고 구현체 이름의 플랫 파일로 둠 (e.g. `ui/LoadingFallback.tsx`, `model/useCalendar.ts`, `constants/errorMessages.ts`). 단위 테스트는 `utils/formatDate.test.ts`처럼 구현 파일 옆에 둠. `shared` 레이어는 기존대로 폴더 + `index.ts`
- 타입 파일은 항상 `types/` 폴더로 감싸고, `index.ts` 없이 내용을 나타내는 이름으로 분리:

  ```
  src/modules/features/user/UserProfile/types/
  ├── user.ts
  ├── address.ts
  ```

### 변수 및 함수명

- 함수명: `camelCase` (작명 방식은 유연하게)
- 변수명: `camelCase`
  - Boolean 타입은 `is`로 시작 (`isOpen`, `isLoading`)
  - 여러 요소를 담는 경우 `s` 접미사 (`todos`)
  - UI가 리스트 형태인 경우 `List` 접미사 (`TodoList`)
- 커스텀 훅: `use` + 훅 이름 (`useTodoQuery`, `useTodoMutation`)
- 컨텍스트: 이름 + `Context` (`modalContext`) — Zustand를 쓰지 않으므로 `store` 네이밍은 사용하지 않음

### 핸들러 함수

- **props로 전달되는** 핸들러: `on`으로 시작 (`onClick`, `onSubmit`)
- **컴포넌트 내부** 핸들러: `handle`로 시작 (`handleClick`, `handleSubmit`)

### 스타일(레이아웃) 요소 네이밍

- `Container`: **2개 이상**의 요소를 감쌀 때
- `Wrapper`: **1개**의 요소를 감쌀 때 (e.g. `<ImageWrapper />`)

## 상수

- 상수: `SNAKE_CASE`
- 함수 **내부** 상수: `camelCase`
- 쿼리키·파라미터 경로 등의 상수는 **query-key factory pattern**으로 관리 (쿼리 키는 `src/shared/api/queryKey/`의 도메인별 파일로 관리)

## 타입

- `any` 금지
- **type vs interface**: 객체는 `interface`, 나머지는 `type`
- **리턴 타입은 명시하지 않음** — 거의 100% 추론되므로 타입 시스템에 맡김
- 초기값에 타입을 지정해 타입 시스템이 알아서 추론하도록 둠 (타입스크립트를 자바스크립트처럼 쓰기)
- API 요청/응답 타입은 `Request`, `Response` 접미사 사용

  ```tsx
  interface UpdateTodoResponse {
    // ...
  }

  const updateTodo = async (): Promise<UpdateTodoResponse> => {
    // ...
  };
  ```

- 컴포넌트 props 타입: `컴포넌트명 + Props` **인터페이스**

  ```tsx
  interface DropdownProps {
    // ...
  }

  export default function Dropdown({ ... }: DropdownProps) {
    const handleClick = () => {
      // ...
    };
  }
  ```

- 훅·함수의 인자 타입: `함수명 + Params` **인터페이스**

  ```tsx
  interface UseCartParams {
    // ...
  }

  const useCart = ({ ... }: UseCartParams) => {};

  export default useCart;
  ```

## 컴포넌트 · 훅 · 함수 작성

- 컴포넌트는 `export default function` 형식으로 구현
- 커스텀 훅은 `use*`로 시작하고 **객체 반환** (배열 X — 확장성)
- 파라미터가 **2개 이상**일 때는 객체 구조 분해로 받기

  ```tsx
  export const dateFormatHandler = ({ day, month, year }: DateFormatHandlerParams) => {};
  ```

## 스타일

- 스타일은 **Emotion**으로 작성. (`@emotion/styled`, `@emotion/react`)
- 디자인 토큰(색상·타이포·간격)은 `shared/provider/themeProvider`의 ThemeProvider를 통해 사용하고, 값을 컴포넌트에 하드코딩하지 않음.
- **Tailwind CSS는 사용하지 않음.** (`className` 유틸 클래스, `clsx`/`tailwind-merge` 미사용)

## 임포트 경로

- 경로 별칭(`@/*` 등)을 사용하고 상대경로 남용 금지 (별칭은 각 패키지 tsconfig/babel 설정 참고)
  - 상대 경로는 본인보다 하위 디렉토리 혹은 같은 디렉토리 내에서만 가능
    (절대 경로와 상대 경로는 줄바꿈 2번으로 구분)

  ```jsx
  // e.g. @/shared/components/composites/ButtonWithIcon/index.tsx

  import Icon from "@/shared/components/primitives/ui/Icon"; // ✅
  //import Icon from "../primitives/ui/Icon"; // ❌

  import useButton from "./model/useButton"; // ✅ 같은 디렉토리(하위) 내에서만 상대경로 허용
  ```
