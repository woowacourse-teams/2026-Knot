---
description: API 요청 관련 가이드라인
paths:
  - "src/shared/api/**/*"
---

# api 가이드라인

## 디렉토리 구조

API 관련 코드는 한곳에서 계층적으로 관리해야 하므로 모두 `src/shared/api` 안에 둠. 쿼리·뮤테이션 훅도 별도 훅 폴더로 빼지 않고 api 폴더 안에 유지.

- `httpClient/` — HTTP 클라이언트(axios) 인스턴스. 인증 토큰 처리·공통 에러 정규화 같은 횡단 관심사는 인터셉터에서 처리.
- `dto/` — 서버와 주고받는 요청·응답 DTO **클래스**와 그 생성자 입력 타입(`Raw`·`Input`). `workspace.ts`처럼 도메인별 파일 하나에 그 도메인의 DTO를 모두 둠. 서버 JSON ↔ 앱 모양의 변환은 클래스 생성자 한 곳에서만 일어남. 작성 규칙은 `.claude/rules/dto-guide.md` 참고.
- `fetch/` — 순수 함수인 fetch를 엔드포인트별 폴더 구조로 관리. restful API 요청 엔드포인트와 `fetch/` 하위 디렉토리 위치가 일치해야 함. 요청·응답 타입은 파일 안에 정의하지 않고 `dto/`에서 가져오며, 응답은 `new XxxResponseDto(response.data)`로 감싸 반환.
  - e.g. `GET /api/v1/users/[id]` → `src/shared/api/fetch/api/v1/users/[id]/index.ts`
  - 폴더 이름의 `fetch`는 네이티브 fetch API가 아니라 **요청 함수**를 뜻함. 실제 요청은 `httpClient/`의 인스턴스로 보냄.
  - 예외: SSE 응답(`Accept: text/event-stream`)을 받는 요청은 axios가 브라우저에서 응답 본문을 조각 단위로 읽지 못하므로 `httpClient` 대신 `sse/requestSseStream`으로 보냄. DTO 하나를 반환하는 대신 `requestSseStream`이 내는 이벤트를 `for await`로 받아 이벤트 DTO로 바꿔 하나씩 내는 async generator로 작성하고, 이름은 다른 요청 함수와 같은 규칙(e.g. `createSearchConversationApi`)을 따름. 스트림을 받기 전 실패 응답은 `requestSseStream`이 상태 코드와 서버 오류 코드를 담은 `SseRequestError`로 throw하므로 요청 함수에서 따로 오류 클래스를 만들지 않음. 스트림을 받은 뒤 실패는 이벤트로 전달.
- `queryKey/` — 쿼리 키는 뮤테이션에서도 쓰이므로 별도 폴더로 분리, `user.ts`처럼 도메인별 파일로 관리.
- `queries/`, `mutations/`, `suspense/`, `prefetch/` — 쿼리·뮤테이션·서스펜스·프리페치 훅을 각각 둠. 작성 규칙은 `.claude/rules/query-hooks.md` 참고.
- `mock/` — 백엔드 연동 전 msw로 응답을 대신하는 mock API. 작성 규칙은 아래 「API mock」 참고.
- `sse/` — SSE 표준 형식과 SSE 요청의 공통 부분만 다룸. 문자열을 이벤트로 자르는 순수 함수(`parseSseEvents`), 응답 본문 스트림을 끝까지 읽어 이벤트를 하나씩 내는 async generator(`readSseStream`, 받는 쪽이 멈추면 `finally`에서 읽기를 취소), 네이티브 `fetch`로 SSE 요청을 보내 `readSseStream`의 이벤트를 그대로 내는 async generator(`requestSseStream`)를 둠. `requestSseStream`은 절대 주소·쿠키를 붙이고 CSRF 헤더는 `httpClient`처럼 GET이 아닌 요청에만 붙이며, 스트림을 받기 전 실패 응답을 같은 파일의 `SseRequestError`(`status`, 본문의 서버 오류 `code`, `message`)로 throw함(본문을 읽지 못하면 `code`는 `UNKNOWN`, 성공인데 본문 없음은 `EMPTY_BODY`, 네트워크 오류·abort는 바꾸지 않고 그대로 던짐). 탐색 같은 도메인과 API 명세의 이벤트 이름을 모름. 요청 함수(`fetch/`)에서만 씀.

## API 요청(fetch) 로직

API 요청 로직은 엔드포인트 폴더의 index.ts 내에 위치하며, 형식은 아래와 같이 작성. 요청·응답 타입은 `dto/`의 도메인 파일에서 가져옴(`.claude/rules/dto-guide.md`). 응답 클래스는 `new`가 필요하므로 값 import, 나머지(`Raw`·요청 클래스)는 `import type`.

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

형식 설명

- API 경로 상수는 `WORKSPACES_API_PATH`처럼 대문자와 \_로 작성. 경로 파라미터가 있으면 `WORKSPACE_API_PATH(workspaceId)`처럼 파라미터를 받아 경로 문자열을 돌려주는 함수로 작성.
- 요청·응답 타입은 `fetch/` 파일 안에 다시 정의하지 않고 `dto/`에서 가져옴. 이름은 `GetWorkspacesResponseDto`처럼 메서드 + 리소스 + `RequestDto`/`ResponseDto`, 서버 JSON 모양은 `…ResponseRaw`.
- `httpClient`의 제네릭에는 **`Raw`**(서버 JSON 모양)를 넣고, 반환은 **`new XxxResponseDto(response.data)`**. `response.data`를 그대로 반환하지 않음. 응답 모양의 변환은 이 `new` 한 곳(정확히는 DTO 생성자)에서만 일어남.
- 요청 본문 인자 타입은 요청 클래스(`PostWorkspaceRequestDto`). 요청 함수는 `new`를 부르지 않고 이미 만들어진 인스턴스를 `data`로 넘기기만 함. 요청 클래스의 `new`는 뮤테이션 훅의 `mutationFn`이 담당(`.claude/rules/query-hooks.md` 「mutation 훅」).
- API 요청 함수 이름은 `getWorkspacesApi`처럼 API 요청 목적 + 메서드 형식 + Api 조합으로 작성.
- API 요청 함수는 async 함수로 작성하며, 요청 성공 시 응답 DTO 인스턴스만 반환(`response` 통째 ❌).
- API 요청 함수에는 JSDoc 주석 필수. `@description`, `@param`, `@returns`, `@example` 네 태그로 목적·매개변수·반환값·사용 예시를 설명하고, 매개변수가 없으면 `@param`만 생략. 응답 필드 하나하나의 설명은 DTO 주석이 맡으므로 여기서 반복하지 않음.

## API mock

mock 데이터는 컴포넌트에 두지 않고 msw로 **네트워크 레이어에서** 응답을 대신함. 컴포넌트와 쿼리 훅은 mock의 존재를 모르므로, 실제 API가 붙어도 컴포넌트 import는 한 줄도 바꾸지 않고 플래그만 끄면 됨. 컴포넌트가 mock 데이터를 직접 import하면 이 이점이 사라지므로 하지 않음.

### 디렉토리 구조

```plaintext
src/shared/api/mock/
├── browser.ts                        # setupWorker — 개발 서버용
├── server.ts                         # setupServer — vitest용
├── handlers/
│   ├── index.ts                      # 전체 핸들러 합치기
│   └── api/v1/chatSessions/index.ts  # fetch/ 와 동일한 경로 구조
├── responses/
│   └── chatSession.ts                # 도메인별 mock 응답 데이터
├── state/
│   └── document.ts                   # (필요할 때만) 요청으로 바뀌는 도메인별 mock 상태
└── types/
    └── chatSession.ts                # 도메인별 mock 전용 응답 타입
```

- `handlers/`는 `fetch/`와 경로 구조를 일치시킴. 엔드포인트가 바뀌면 요청 함수와 핸들러가 항상 같이 바뀌므로 `fetch/`의 "URL과 폴더 구조 일치" 규칙을 그대로 이어받음.
- `responses/`는 `queryKey/`처럼 도메인별 플랫 파일. 데이터는 엔드포인트가 아니라 도메인 단위로 바뀌고(세션 목록·세션 상세가 같은 `ChatSession`을 공유), 통합 테스트의 기대값(응답 DTO로 변환해서 씀, `test-strategy.md` 「기대값」)으로도 쓰이기 때문.
- `state/`는 변경 요청 뒤에 조회 응답이 달라져야 하는 도메인에만 두는 mock 상태. 대부분의 도메인에는 없음. 규칙은 아래 「요청으로 바뀌는 상태」 참고.
- `types/`는 `responses/`와 같은 도메인별 파일로 둔 mock 전용 타입. mock 응답은 네트워크로 나가는 **서버 JSON 모양**이므로 여기 타입은 `dto/`의 `Raw`(생성자 입력)에 대응하며, DTO 클래스(앱 모양)가 아님. mock의 데이터 타입은 모두 여기서 별도로 정의하고 `dto/`·`fetch/`의 타입을 import하지 않음. 실제 요청·응답 모양의 정본은 `dto/`.

### mock 응답 데이터

`responses/`의 파일은 `mock/types/`의 타입을 `satisfies`로 씀. `dto/`와 import로 묶이지 않으므로 백엔드 스펙이 바뀌면 `dto/`(`Raw`·클래스)와 `mock/types/`를 함께 갱신함.

```typescript
// src/shared/api/mock/responses/chatSession.ts
import type { ChatSession } from "@api/mock/types/chatSession";

const HOUR = 60 * 60 * 1000;

// 지난 시각을 고정값으로 두면 언젠가 전부 "이전"으로 묶이므로 지금을 기준으로 생성
const fromNow = (elapsed: number) =>
  new Date(Date.now() - elapsed).toISOString();

export const chatSessionsResponse = [
  {
    id: 100,
    title: "DB 기술 선정 관련 문서",
    createdAt: fromNow(2 * HOUR),
    lastMessageAt: fromNow(30 * 1000),
  },
] satisfies ChatSession[];
```

- 변수명은 `chatSessionsResponse`처럼 도메인 + `Response`로 작성.
- **성공 기본값 하나만 둠.** 빈 목록·404·지연 같은 변형은 응답 파일을 늘리지 말고 테스트에서 `mockServer.use(...)`로 그 케이스에서만 덮음. `chatSessionsEmpty`, `chatSessionsError`로 불어나면 어느 게 기본인지 알 수 없어짐.

### 핸들러

```typescript
// src/shared/api/mock/handlers/api/v1/chatSessions/index.ts
import { http, HttpResponse } from "msw";

import { CHAT_SESSIONS_API_PATH } from "@api/fetch/api/v1/chatSessions";
import { chatSessionsResponse } from "@api/mock/responses/chatSession";

// baseURL이 환경변수라 오리진은 와일드카드로 두고, 경로는 fetch의 상수를 그대로 씀
export const chatSessionsHandlers = [
  http.get(`*${CHAT_SESSIONS_API_PATH}`, () =>
    HttpResponse.json(chatSessionsResponse),
  ),
];
```

```typescript
// src/shared/api/mock/handlers/index.ts
import { chatSessionsHandlers } from "./api/v1/chatSessions";

export const handlers = [...chatSessionsHandlers];
```

- 경로는 `fetch/`의 경로 상수를 재사용해 요청 함수와 핸들러가 어긋나지 않게 함.
- 경로 파라미터가 있는 엔드포인트는 `fetch/`의 상수가 실제 id를 채우는 함수라 그대로 못 쓰므로, 그 경우에만 `*/api/v1/chat-sessions/:sessionId` 패턴을 핸들러에 직접 적고 폴더 위치(`handlers/api/v1/chatSessions/[sessionId]/`)로 대응 관계를 드러냄.
- 엔드포인트 하나를 추가하는 비용은 **응답 1개 + 핸들러 1개 + 등록 1줄**(새 도메인이면 타입 1개 추가).

### 요청으로 바뀌는 상태

핸들러는 기본적으로 `responses/`의 값을 그대로 돌려줌. 변경 요청 뒤에 다른 조회 응답이 달라져야 화면 흐름을 확인할 수 있는 경우에만 `state/`에 도메인별 파일을 둠. (예: 문서를 확인하면 문서 상세의 내 확인 상태와 확인 수, 확인 대상 목록의 내 항목이 함께 바뀜)

- **`responses/`의 기본값은 바꾸지 않음.** 테스트의 기대값으로 쓰이기 때문. `state/`는 바뀐 것만 기억하고, 기본값에서 바뀐 응답을 계산해 돌려줌.
- 그 도메인의 핸들러는 `responses/`를 직접 읽지 않고 `state/`의 함수를 거쳐 읽고 씀. 한 핸들러라도 직접 읽으면 응답끼리 어긋남.
- 상태를 지우는 `reset{Domain}MockState` 함수를 함께 두고 `vitest.setup.ts`의 `afterEach`에서 부름. 지우지 않으면 앞 테스트가 보낸 요청이 뒤 테스트의 응답을 바꿈.
- 스토리북은 스토리를 옮겨도 미리보기 화면을 새로고침하지 않아 mock 상태가 남음. 그 도메인의 상태를 **바꾸거나 읽는** 컴포넌트의 스토리 파일은 meta `loaders`에서도 `reset{Domain}MockState`를 부름. (예: 확인 버튼 스토리에서 누른 확인이 확인 수 스토리에 1명 많게 보임)
- 빈 목록·에러 같은 **변형을 만드는 용도로 쓰지 않음.** 변형은 지금처럼 그 테스트·스토리에서 핸들러를 덮음.
- 개발 서버에서는 페이지를 새로고침하면 기본 상태로 돌아감.

### 켜고 끄기

진입점은 `server.ts`(`setupServer`)·`browser.ts`(`setupWorker`) 2개이며 엔드포인트마다가 아니라 최초 1회만 만듦.

- 테스트: `vitest.setup.ts`에서 전역으로 켬. `listen({ onUnhandledRequest: "error" })`로 두어 핸들러 없는 요청을 실패로 드러내고, `afterEach`에서 `resetHandlers()`.
- 개발 서버: `src/index.tsx`에서 `API_MOCKING` 플래그로 감싸고 **동적 import**. 실제 API 연동 후에는 이 플래그만 꺼서 실제 서버로 전환하고, 테스트는 그대로 mock을 씀. 정적 import하면 msw가 프로덕션 번들에 들어감. 플래그는 `webpack.config.js`의 DefinePlugin에 `API_BASE_URL`과 나란히 추가하고, 브라우저용은 `mockServiceWorker.js`가 `devServer.static` 경로에서 서빙돼야 함.

### 생명주기

**`mock/`은 실제 API 연동 후에도 지우지 않고 계속 관리함.** 역할만 바뀜.

| 시점             | 역할                                                       |
| ---------------- | ---------------------------------------------------------- |
| 실제 API 연동 전 | 테스트용 + 개발 서버의 실제 API 대체용 (`API_MOCKING` 켬)  |
| 실제 API 연동 후 | 테스트용으로 유지. 개발 서버는 플래그를 꺼서 실제 API 사용 |

- 실제 서버로 테스트하면 DB가 바뀌고 특정 플로우에 도달하기 어려워 위험 부담과 공수가 크므로, 자동화 테스트는 연동 후에도 계속 mock으로 돌림.
- 실제 API 연동 검증은 자동화 테스트가 아니라 **QA에서 실제 API를 붙여** 수행.
- 따라서 백엔드 스펙이 바뀌면 mock을 지우는 게 아니라 `dto/`·`mock/types/`·`responses/`를 함께 갱신함.

### 의존 규칙

- 의존은 `mock/` → `fetch/` **단방향**이며 `fetch/`에서는 경로 상수만 가져옴. 데이터 타입은 `mock/types/`에 서버 JSON 모양으로 두고 `dto/`·`fetch/`의 타입을 import하지 않음. `mock/`에서 DTO 클래스를 `new`하지도 않음. 프로덕션 코드는 `mock/`을 import하지 않음. (`grep -r "api/mock" src --exclude-dir=mock`으로 확인. 결과는 `API_MOCKING` 플래그 안에서 `mock/browser`를 동적 import하는 `src/index.tsx` 한 곳만 허용)
- `mock/`에는 **실제 API 응답을 흉내내는 것만** 둠. 대응하는 엔드포인트가 아직 없는 임시 UI 데이터는 핸들러를 만들 수 없으므로 스펙이 정해질 때까지 컴포넌트에 코로케이션.
