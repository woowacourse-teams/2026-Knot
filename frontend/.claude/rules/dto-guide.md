---
description: API 요청·응답 DTO 클래스의 위치·파일 분리·생성자·네이밍·주석·의존 규칙 가이드라인
paths:
  - "src/shared/api/**/*"
---

# DTO 가이드라인

DTO(Data Transfer Object)는 **서버와 주고받는 데이터의 경계를 담당하는 클래스**. 요청 본문·응답 본문과 그 안에서 반복되는 조각을 모두 `src/shared/api/dto/`에서 관리하고, 요청 함수(`fetch/`)와 뮤테이션 훅(`mutations/`)은 타입을 파일 안에 정의하지 않고 여기서 가져다 씀.

DTO를 `interface`가 아니라 `class`로 두는 목적은 런타임 검증이 아니라 **경계에서의 변환 지점 단일화**. 서버 JSON → 앱에서 쓰는 모양, 앱 입력 → 요청 본문으로 바꾸는 코드를 DTO 클래스의 생성자 한 곳에 모아, 백엔드 스펙이 바뀌면 그 도메인 파일만 고치면 되게 함. 서버 응답이 `interface`로 그대로 흘러가면 필드 이름 하나가 바뀔 때 그 필드를 읽는 컴포넌트 전부를 찾아다녀야 함.

이 방식의 알려진 비용은 아래 두 가지이며, 감수하고 채택함.

- **structural sharing 대상이 아님.** TanStack Query는 refetch 결과를 `replaceEqualDeep`으로 이전 `data`와 비교해 같으면 참조를 유지하는데, 클래스 인스턴스는 plain object가 아니라 이 비교에서 제외됨. 따라서 내용이 같아도 **refetch마다 `data` 참조가 바뀜.** 쿼리 훅의 `data`를 `useEffect`·`useMemo`의 deps에 넣을 때는 이를 전제로 하고, 참조가 아니라 `data.id` 같은 원시값을 deps에 둠. (`query-hooks.md`)
- **필드 누락이 컴파일 에러.** `tsconfig`가 `strict`(→ `strictPropertyInitialization`)이므로 생성자에서 모든 클래스 필드를 대입해야 컴파일됨. 스펙에 필드가 추가되면 `Raw`·`Input` 인터페이스와 클래스 필드와 생성자 대입 세 곳을 함께 고쳐야 하며, 하나라도 빠지면 `tsc`가 잡음. 번거롭지만 스펙 변경이 조용히 새는 것보다 낫다고 판단.

## 디렉토리 구조

`dto/`는 `queryKey/`·`mock/types/`처럼 **도메인별 플랫 파일**. 엔드포인트별 폴더가 아님.

```plaintext
src/shared/api/dto/
├── auth.ts                 # 인증·로그인 회원
├── workspace.ts            # 워크스페이스
├── workspaceInvitation.ts  # 워크스페이스 초대
├── chatSession.ts          # 대화 세션
└── chatMessage.ts          # 대화 메시지
```

- **같은 도메인의 DTO는 한 파일에 둠.** 조회·생성·수정처럼 엔드포인트가 여러 개여도 도메인이 같으면 파일을 나누지 않음. 응답 모양은 엔드포인트가 아니라 도메인 단위로 함께 바뀌기 때문(워크스페이스 목록 항목과 워크스페이스 상세가 같은 필드를 공유).
- 새 엔드포인트의 DTO는 기존 도메인 파일에 추가하고, 새 파일은 **새 도메인일 때만** 만듦.
- 파일명은 `queryKey/`·`mock/types/`와 같은 도메인 이름을 씀. 세 곳의 파일명이 어긋나면 어느 도메인인지 찾기 어려워짐.
- `dto/index.ts`는 만들지 않음. 항상 `@api/dto/workspace`처럼 도메인 파일을 직접 import.

## 파일 내용

`dto/` 파일에는 **두 가지만** 둠.

1. **DTO 클래스** — 요청 본문·응답 본문·공유 조각 모두 `class`. 앱이 실제로 쓰는 모양.
2. **생성자 입력 타입** — 클래스 생성자가 받는 `interface`. 응답이면 서버 JSON 모양(`Raw`), 요청이면 앱 쪽 값(`Input`).

그 외 상수·함수·enum 같은 런타임 코드는 두지 않음. 런타임 값이 필요하면 `fetch/`(경로 상수)나 `shared/constants`에 둠.

- 파일 안 순서는 **공유 조각(`Raw` + 클래스) → 엔드포인트별 요청·응답(`Raw`/`Input` + 클래스)**. 엔드포인트별 그룹 위에는 `// GET /api/v1/workspaces`처럼 메서드와 경로를 적은 구분 주석을 둠.
- 여러 엔드포인트가 같은 모양을 쓰면 공유 조각 클래스 하나를 정의하고, 엔드포인트 응답 클래스는 생성자에서 `new`로 그 조각을 만들어 필드에 담음. 응답 전체가 공유 조각과 같아도 엔드포인트마다 클래스를 따로 두어야 나중에 한쪽만 바뀔 때 `fetch/`를 고치지 않아도 됨.

### 생성자

**생성자는 변환만 한다.** 입력을 받아 클래스 필드에 대입하는 것이 전부이며, 다음만 허용.

| 허용                                 | 예시                                                      |
| ------------------------------------ | --------------------------------------------------------- |
| 필드 선별 (앱이 안 쓰는 필드 버리기) | `raw.internalFlag`를 대입하지 않음                        |
| 기본값                               | `this.members = raw.members ?? []`                        |
| `null` 정규화                        | `this.description = raw.description ?? ""`                |
| 이름 변경                            | `this.lastViewedAt = raw.last_viewed_at`                  |
| 형식 변환 (필요할 때만)              | `this.name = name.trim()`                                 |
| 중첩 조각을 공유 DTO로 감싸기        | `raw.items.map((item) => new WorkspaceListItemDto(item))` |

- **검증(throw) 목적으로 쓰지 않음.** 생성자에서 `throw`하거나 형식 검사를 하지 않음. 응답 검증이 필요해지면 별도 계층으로 논의하고, DTO에 끼워 넣지 않음.
- 변환할 게 없는 필드는 `this.id = raw.id`처럼 **그대로 대입.** "변환이 없으니 생략"하지 않음(`strict`라 컴파일도 안 됨).
- **메서드·getter·`#private` 필드 금지.** 필드만 둠. 요청 DTO는 `httpClient`의 `data`로 넘어가 axios가 enumerable 필드를 그대로 직렬화하므로 getter는 본문에 실리지 않고 `#private`는 직렬화 대상이 아님. 응답 DTO도 같은 규칙을 적용해 두 종류의 클래스 모양을 일치시킴. 파생 값이 필요하면 `shared/utils`나 사용하는 쪽에서 계산.

### 응답 DTO

- 생성자 입력은 **서버 JSON 모양** — `{Method}{Resource}ResponseRaw`, 공유 조각은 `{Name}Raw`. 스펙 문서(swagger)의 필드 이름·타입을 그대로 옮김.
- 클래스 필드는 **앱에서 쓰는 모양.** 서버 필드명이 앱 관례와 다르면 여기서 바꿈.
- 중첩 조각은 생성자에서 `raw.items.map((item) => new ItemDto(item))`처럼 공유 조각 클래스로 감쌈. 조각의 변환 규칙이 조각 클래스 한 곳에만 있게 하기 위함.
- `httpClient` 제네릭에는 클래스가 아니라 **`Raw` 타입**을 넣음. 응답 본문은 plain JSON이지 클래스 인스턴스가 아니기 때문.

### 요청 DTO

- 생성자 입력은 **앱 쪽 값** — `{Method}{Resource}RequestInput`. 컴포넌트·훅이 넘기는 plain object의 모양.
- 클래스 필드는 **서버로 나가는 본문 모양.** 인스턴스가 그대로 `data`로 직렬화되므로 필드 이름은 서버 스펙을 따름.
- 요청 함수의 인자 타입은 클래스(`PostWorkspaceRequestDto`)이며, `new`는 뮤테이션 훅이 함. (아래 「`new`를 부르는 곳」)

## `new`를 부르는 곳

`new`는 **`shared/api` 안의 두 곳에서만** 부름. 경계 변환이 이 두 곳에서만 일어나야 "변환 지점 단일화"가 성립.

| 방향 | 위치                           | 코드                                                     |
| ---- | ------------------------------ | -------------------------------------------------------- |
| 응답 | `fetch/`의 요청 함수           | `return new GetWorkspacesResponseDto(response.data)`     |
| 요청 | `mutations/` 훅의 `mutationFn` | `createWorkspaceApi(new PostWorkspaceRequestDto(input))` |

- 요청 함수는 `httpClient<XxxResponseRaw>`로 받은 `response.data`를 응답 클래스로 감싸 반환. `response.data`를 그대로 반환하지 않음.
- 뮤테이션 훅의 `mutationFn`은 `RequestInput`을 받아 `new XxxRequestDto(input)`을 만들어 요청 함수에 넘김. 뮤테이션 훅을 부르는 컴포넌트는 **plain object(`Input` 모양)** 만 넘기고 `new`를 모름.
- 컴포넌트·`modules/`·`pages/`·`shared/hooks`는 `dto/`를 import하지도, `new`를 부르지도 않음. 쿼리 훅의 `data`와 뮤테이션 훅의 `mutate` 인자에서 타입을 추론받아 씀.

## 네이밍

| 종류                  | 형식                             | 예시                                             |
| --------------------- | -------------------------------- | ------------------------------------------------ |
| 요청 클래스           | `{Method}{Resource}RequestDto`   | `PostWorkspaceRequestDto`                        |
| 요청 생성자 입력      | `{Method}{Resource}RequestInput` | `PostWorkspaceRequestInput`                      |
| 응답 클래스           | `{Method}{Resource}ResponseDto`  | `GetWorkspacesResponseDto`                       |
| 응답 생성자 입력      | `{Method}{Resource}ResponseRaw`  | `GetWorkspacesResponseRaw`                       |
| 공유 조각 클래스      | `{Name}Dto`                      | `WorkspaceInvitationDto`, `WorkspaceListItemDto` |
| 공유 조각 생성자 입력 | `{Name}Raw`                      | `WorkspaceInvitationRaw`, `WorkspaceListItemRaw` |

- `{Method}{Resource}`는 요청 함수 이름과 대응하게 지음. `getWorkspacesApi` ↔ `GetWorkspacesResponseDto`, `createWorkspaceApi` ↔ `PostWorkspaceRequestDto` / `PostWorkspaceResponseDto`.
- 접미사 `Dto`는 생략하지 않음. 이름만으로 "서버와 주고받는 모양"임을 드러내 컴포넌트의 props·모델 타입과 구분하기 위함.
- `Raw`는 "서버에서 온 그대로", `Input`은 "앱이 넘긴 그대로"를 뜻함. 둘 다 클래스 생성자의 입력이며, 클래스 밖으로 흘러가지 않음.
- 클래스는 `class`, 생성자 입력은 `interface`. 객체는 `interface`라는 전역 규칙의 DTO 예외는 `general-code-convention.md` 참고.

## 주석

DTO는 백엔드 스펙을 코드로 옮긴 것이므로, 스펙 문서(swagger)를 열지 않아도 필드의 의미와 제약을 알 수 있어야 함. **주석은 필수이며, 이름을 되풀이하는 주석은 주석으로 치지 않음.**

- **파일 상단**: 도메인 설명 한 줄과 이 파일이 다루는 엔드포인트 목록(메서드 + 경로).
- **클래스마다**: 무엇의 요청/응답인지 한 줄. 공유 조각이면 어느 엔드포인트들이 공유하는지. 상태 코드에 따라 모양이 달라지거나 같은 모양을 쓰는 경우(200·201)처럼 요청 함수 쪽에서 알아야 할 사실도 여기에 적음.
- **클래스 필드마다** `/** */` 한 줄 이상. 아래 중 해당하는 것을 반드시 포함:
  - 의미 (`id`라면 무엇의 ID인지)
  - `null`·`undefined`가 오는 조건 (`/** 본 적이 없으면 null */`)
  - 형식 (`ISO 8601`, 절대 URL, 6자 대문자 코드)
  - 제약 (최대 길이, 허용 문자, 범위, 단위)
  - 예시 값 (형식이 눈에 안 들어오는 문자열·코드)
  - **생성자에서 변환한 내용** (`/** 서버 null → 빈 문자열 */`, `/** 앞뒤 공백 제거 */`). 변환이 있는 필드는 무엇을 어떻게 바꿨는지 반드시 적음.
- `Raw`·`Input` 인터페이스의 필드 주석은 클래스 필드 주석과 중복이면 생략 가능. 인터페이스 자체에는 한 줄 설명을 둠. 서버 쪽에만 있는 사실(서버 필드명이 다른 이유 등)은 `Raw` 쪽에 적음.
- JSDoc 태그(`@property`)로 클래스 위에 몰아 쓰지 않고 **필드 위에 직접** 씀. 필드 위 주석이어야 사용하는 쪽에서 `data.expiresAt`에 커서를 올렸을 때 IDE가 보여줌.
- 주석 언어는 한국어. 스펙이 바뀌면 클래스·`Raw`·주석을 **같은 커밋**에서 갱신함. 타입만 바뀌고 주석이 옛 스펙을 설명하면 없느니만 못함.

## 예시

```typescript
// src/shared/api/dto/workspace.ts

/**
 * 워크스페이스 DTO
 *
 * - GET  /api/v1/workspaces
 * - POST /api/v1/workspaces
 */

/** 워크스페이스 목록 항목의 서버 응답 모양 */
export interface WorkspaceListItemRaw {
  id: number;
  name: string;
  lastViewedAt: string | null;
}

/** 워크스페이스 목록 항목. 목록·상세 응답이 공유 */
export class WorkspaceListItemDto {
  /** 워크스페이스 ID */
  id: number;
  /** 워크스페이스 이름 */
  name: string;
  /** 마지막으로 본 시각(ISO 8601). 본 적이 없으면 null */
  lastViewedAt: string | null;

  constructor(raw: WorkspaceListItemRaw) {
    this.id = raw.id;
    this.name = raw.name;
    this.lastViewedAt = raw.lastViewedAt;
  }
}

// GET /api/v1/workspaces

/** 워크스페이스 목록 조회의 서버 응답 모양 */
export interface GetWorkspacesResponseRaw {
  lastViewedWorkspaceId: number | null;
  workspaces: WorkspaceListItemRaw[];
}

/** 내가 속한 워크스페이스 목록 조회 응답 */
export class GetWorkspacesResponseDto {
  /** 마지막으로 본 워크스페이스 ID. 없으면 null */
  lastViewedWorkspaceId: number | null;
  /** 워크스페이스 목록 */
  workspaces: WorkspaceListItemDto[];

  constructor(raw: GetWorkspacesResponseRaw) {
    this.lastViewedWorkspaceId = raw.lastViewedWorkspaceId;
    this.workspaces = raw.workspaces.map(
      (item) => new WorkspaceListItemDto(item),
    );
  }
}

// POST /api/v1/workspaces

/** 워크스페이스 생성 시 앱이 넘기는 값 */
export interface PostWorkspaceRequestInput {
  name: string;
}

/** 워크스페이스 생성 요청 본문 */
export class PostWorkspaceRequestDto {
  /** 워크스페이스 이름. 앞뒤 공백 제거, 1~20자 */
  name: string;

  constructor({ name }: PostWorkspaceRequestInput) {
    this.name = name.trim();
  }
}

/** 워크스페이스 생성의 서버 응답 모양 */
export interface PostWorkspaceResponseRaw {
  id: number;
}

/** 워크스페이스 생성 응답 */
export class PostWorkspaceResponseDto {
  /** 생성된 워크스페이스 ID */
  id: number;

  constructor(raw: PostWorkspaceResponseRaw) {
    this.id = raw.id;
  }
}
```

요청 함수는 응답 클래스를 값으로, `Raw`와 요청 클래스를 `import type`으로 가져옴. `httpClient`의 제네릭에는 `Raw`를, 요청 본문 인자에는 요청 클래스를, 반환에는 `new XxxResponseDto(response.data)`를 씀. 요청 함수의 전체 형식과 JSDoc은 `api-guide.md`의 「API 요청(fetch) 로직」 참고.

```typescript
// src/shared/api/fetch/api/v1/workspaces/index.ts
import {
  GetWorkspacesResponseDto,
  PostWorkspaceResponseDto,
  type GetWorkspacesResponseRaw,
  type PostWorkspaceRequestDto,
  type PostWorkspaceResponseRaw,
} from "@api/dto/workspace";
import { httpClient } from "@api/httpClient";

export const WORKSPACES_API_PATH = "/api/v1/workspaces";

/**
 * @description 내가 속한 워크스페이스 목록을 조회합니다
 * @returns 마지막으로 본 워크스페이스 ID와 워크스페이스 목록
 * @example
 * const { workspaces } = await getWorkspacesApi();
 */
export const getWorkspacesApi = async () => {
  const response = await httpClient<GetWorkspacesResponseRaw>({
    method: "get",
    url: WORKSPACES_API_PATH,
  });

  return new GetWorkspacesResponseDto(response.data);
};

/**
 * @description 워크스페이스를 생성합니다
 * @param body - 워크스페이스 생성 요청 본문
 * @returns 생성된 워크스페이스 ID
 * @example
 * const { id } = await createWorkspaceApi(new PostWorkspaceRequestDto({ name: "Knot 팀" }));
 */
export const createWorkspaceApi = async (body: PostWorkspaceRequestDto) => {
  const response = await httpClient<PostWorkspaceResponseRaw>({
    method: "post",
    url: WORKSPACES_API_PATH,
    data: body,
  });

  return new PostWorkspaceResponseDto(response.data);
};
```

뮤테이션 훅은 `Input`을 받아 요청 클래스를 `new`로 만들어 요청 함수에 넘김. 훅 형식은 `query-hooks.md`의 「mutation 훅」 참고.

```typescript
// src/shared/api/mutations/useCreateWorkspaceMutation/index.ts
import { useMutation } from "@tanstack/react-query";

import {
  PostWorkspaceRequestDto,
  type PostWorkspaceRequestInput,
} from "@api/dto/workspace";
import { createWorkspaceApi } from "@api/fetch/api/v1/workspaces";

const useCreateWorkspaceMutation = () => {
  return useMutation({
    mutationFn: (input: PostWorkspaceRequestInput) =>
      createWorkspaceApi(new PostWorkspaceRequestDto(input)),
  });
};

export default useCreateWorkspaceMutation;
```

## 의존 규칙

- `dto/`는 **아무것도 import하지 않음.** 다른 도메인의 DTO가 필요할 때만 `dto/` 안의 다른 파일을 가져오고(중첩 조각을 `new`해야 하면 값 import), 두 파일이 서로를 import하는 순환은 만들지 않음.
- `fetch/`·`mutations/`는 `new`가 필요하므로 `dto/`를 **값 import** 할 수 있음. `fetch/`는 응답 클래스를 값으로, `mutations/`는 요청 클래스를 값으로 가져오고, 나머지(`Raw`·`Input`·요청 함수 인자용 요청 클래스)는 `import type`.
- `queries/`·`suspense/`·`prefetch/`는 `dto/`를 import하지 않음. 요청 함수의 반환 타입 추론으로 충분함.
- 요청·응답 타입을 `fetch/`·`mutations/` 파일 안에 다시 정의하지 않음.
- `mock/`은 `dto/`를 import하지 않음. mock 응답 데이터는 서버 JSON 모양이므로 `mock/types/`에 `Raw`에 대응하는 타입을 별도로 둠. (`api-guide.md` 「API mock」)
- **`shared/api` 밖(`modules/`, `pages/`, `shared/components`, `shared/hooks`)은 `dto/`를 import하지 않음.** 컴포넌트·훅은 쿼리 훅의 `data`와 뮤테이션 훅의 `mutate` 인자에서 타입을 추론받아 씀. 서버 응답 모양이 컴포넌트까지 직접 흘러가면 스펙 변경이 화면 전체로 번지기 때문. (`grep -rn "api/dto" src --exclude-dir=api`로 확인. 결과가 없어야 함)
- `new XxxDto(...)`는 `fetch/`·`mutations/`에서만 부름. (`grep -rn "new [A-Za-z]*Dto(" src`로 확인. 결과가 `shared/api/dto`·`shared/api/fetch`·`shared/api/mutations` 밖에 없어야 함)
- API 요청·응답 타입을 위해 `src/shared/types/`를 만들지 않음. 전역 `types/`는 서버 스펙과 무관한 공용 타입 자리.
