---
description: React Query를 활용한 API 통신용 커스텀 훅 작성 가이드라인
paths:
  - "src/shared/api/**/*"
---

# query hooks 가이드라인

## 개요

React Query를 활용한 API 통신용 커스텀 훅 작성 가이드라인.

## 디렉토리 구조

API 관련 코드는 한곳에서 계층적으로 관리하므로, tanstack-query를 래핑한 커스텀 훅은 별도 훅 폴더로 빼지 않고 모두 `src/shared/api/` 안에 둠.

- query 훅은 `src/shared/api/queries/` 디렉토리에 작성.
- mutation 훅은 `src/shared/api/mutations/` 디렉토리에 작성.
- suspenseQuery 훅은 `src/shared/api/suspense/` 디렉토리에 작성.// TODO:
- prefetchQuery 훅은 `src/shared/api/prefetch/` 디렉토리에 작성.
- 쿼리 키는 `src/shared/api/queryKey/` 디렉토리의 도메인별 파일(`todo.ts`, `user.ts`)에서 query-key factory pattern으로 관리. 쿼리 키가 뮤테이션(무효화 등)에서도 쓰이므로 별도 폴더로 분리.

## 네이밍

tanstack-query 훅 이름을 그대로 이어받아, 같은 리소스의 어떤 변형(suspense/prefetch)인지 이름만으로 드러나도록 작성.

| 래핑 대상        | 훅 이름 예시            | 위치                                       |
| ---------------- | ----------------------- | ------------------------------------------ |
| useQuery         | `useTodosQuery`         | `queries/useTodosQuery/index.ts`           |
| useSuspenseQuery | `useTodosSuspenseQuery` | `suspense/useTodosSuspenseQuery/index.ts`  |
| usePrefetchQuery | `useTodosPrefetchQuery` | `prefetch/useTodosPrefetchQuery/index.ts`  |
| useMutation      | `useUpdateTodoMutation` | `mutations/useUpdateTodoMutation/index.ts` |

## tanstack-query 커스텀 훅 작성 규칙

- tanstack-query 훅을 얇게 래핑하는 형태로 작성. 반환값이 있는 훅(useQuery, useSuspenseQuery, useMutation)은 그대로 return, 반환값이 없는 usePrefetchQuery는 호출만 함.
- queryKey는 훅 안에서 직접 만들지 않고, `queryKey/`의 도메인별 파일에서 가져와 사용.
- 같은 리소스의 query·suspense·prefetch 훅은 **동일한 queryKey factory 함수와 동일한 fetch 함수** 사용 필수. 키가 어긋나면 prefetch로 채운 캐시를 query/suspense 훅이 읽지 못함.
- 훅의 인자는 `함수명 + Params` 형태의 interface로 정의.
- 훅 이름과 동일한 디렉토리를 만들고 그 안에 `index.ts`로 작성.

### 쿼리 키 (query-key factory)

```typescript
// src/shared/api/queryKey/todo.ts
export const todoKeys = {
  all: ["todos"] as const,
  list: (memberId: string) => [...todoKeys.all, "list", memberId] as const,
};
```

### query 훅

```typescript
// src/shared/api/queries/useTodosQuery/index.ts
import { useQuery } from "@tanstack/react-query";
import { getTodosApi } from "@/shared/api/fetch/api/v1/todos";
import { todoKeys } from "@/shared/api/queryKey/todo";

interface UseTodosQueryParams {
  memberId: string;
}

const useTodosQuery = ({ memberId }: UseTodosQueryParams) => {
  return useQuery({
    queryKey: todoKeys.list(memberId),
    queryFn: () => getTodosApi(memberId),
  });
};

export default useTodosQuery;
```

### suspense query 훅

```typescript
// src/shared/api/suspense/useTodosSuspenseQuery/index.ts
import { useSuspenseQuery } from "@tanstack/react-query";
import { getTodosApi } from "@/shared/api/fetch/api/v1/todos";
import { todoKeys } from "@/shared/api/queryKey/todo";

interface UseTodosSuspenseQueryParams {
  memberId: string;
}

const useTodosSuspenseQuery = ({ memberId }: UseTodosSuspenseQueryParams) => {
  return useSuspenseQuery({
    queryKey: todoKeys.list(memberId),
    queryFn: () => getTodosApi(memberId),
  });
};

export default useTodosSuspenseQuery;
```

### prefetch 훅

usePrefetchQuery는 반환값이 없으므로 return 하지 않음. Suspense 경계 바깥(부모 컴포넌트)에서 호출해, suspense 컴포넌트가 마운트되기 전에 캐시를 미리 채우는 용도.

```typescript
// src/shared/api/prefetch/useTodosPrefetchQuery/index.ts
import { usePrefetchQuery } from "@tanstack/react-query";
import { getTodosApi } from "@/shared/api/fetch/api/v1/todos";
import { todoKeys } from "@/shared/api/queryKey/todo";

interface UseTodosPrefetchQueryParams {
  memberId: string;
}

const useTodosPrefetchQuery = ({ memberId }: UseTodosPrefetchQueryParams) => {
  usePrefetchQuery({
    queryKey: todoKeys.list(memberId),
    queryFn: () => getTodosApi(memberId),
  });
};

export default useTodosPrefetchQuery;
```
