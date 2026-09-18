---
paths:
  - "src/**/test.ts"
  - "src/**/test.tsx"
  - "src/**/*.test.ts"
  - "src/__test__/**"
description: 단위·통합·E2E 테스트의 위치와 대상을 정의하는 테스트 전략 가이드라인. 테스트 코드 작성·배치 전 필독.
---

# 테스트 전략 가이드라인

UI 테스트와 스토리북은 현실적인 리소스를 고려해 일단 보류, **단위·통합·E2E 세 가지만** 가져감.

AI로 기능 개발 시 검증 루프가 필요한데, 검증 루프를 테스트 코드로 만들어 두면 개발 효율이 좋아짐. 기능 구현 시 테스트 코드를 검증 루프로 활용할 것.

## 테스트 종류별 위치와 대상

| 종류        | 위치                                                                                                                                                                                 | 대상                                    |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------- |
| 단위 테스트 | 유틸 구현 파일 옆에 나란히 코로케이션. 세그먼트 `utils`는 `utils/formatDate.ts` + `utils/formatDate.test.ts`, `shared/utils`는 기존대로 `formatDate/index.ts` + `formatDate/test.ts` | 유틸 함수                               |
| 통합 테스트 | 최종 책임 컴포넌트(대부분 섹션 단위의 widgets) 폴더 안의 `test.tsx`                                                                                                                  | 패칭부터 UI까지 하나의 유저 플로우 전체 |
| E2E 테스트  | 전역 `src/__test__/`에 페이지 이름을 붙인 파일 (예: `dashboardPage.test.ts`)                                                                                                         | 페이지 단위 수행 권장                   |

## 배치 규칙

- E2E 테스트를 페이지 폴더에 두기는 애매하므로 전역 `__test__/` 폴더로 분리. 페이지 폴더에 두지 않음.
- 테스트 파일은 "인덱스 제외 나머지는 폴더로 둔다"는 네이밍 규칙의 예외. 세그먼트 `utils`의 단위 테스트는 `{구현체 이름}.test.ts`, `shared/utils`의 단위 테스트는 폴더 안 `test.ts`, JSX를 렌더링하는 컴포넌트 통합 테스트는 컴포넌트 폴더 바로 아래 `test.tsx`로 둠.
- API 응답은 `vitest.setup.ts`에서 전역으로 켠 msw 서버(`shared/api/mock/server`)가 대신함. 실제 서버로 테스트하면 DB가 바뀌고 특정 플로우에 도달하기 어려우므로 **실제 API 연동 후에도 자동화 테스트는 계속 mock으로 돌리고**, 실제 API 검증은 QA에서 수행. 테스트가 요청 함수나 쿼리 훅을 직접 모킹하지 않고, 기대값은 문자열로 박지 않고 `shared/api/mock/responses`의 mock 응답을 **응답 DTO 클래스로 변환한 값**에서 가져옴(아래 「기대값」). 특정 케이스(빈 목록·에러)는 해당 테스트에서 `mockServer.use(...)`로 덮음. (`.claude/rules/api-guide.md`의 「API mock」)
- **훅은 테스트하지 않음.** 단위 테스트 대상은 유틸 함수이며, 훅의 동작은 해당 컴포넌트의 통합 테스트로 검증.
- UI 테스트·스토리북 관련 파일(`*.stories.tsx` 등)은 보류 상태이므로 새로 만들지 않음.

## 기대값

mock 응답(`shared/api/mock/responses`)은 네트워크로 나가는 **서버 JSON(`Raw`) 모양**이고, 컴포넌트가 받는 건 `fetch/`에서 `new XxxResponseDto(response.data)`를 거친 **앱 모양**. 생성자가 값을 바꾸는 필드(`null → ""`, 이름 변경, `trim`)는 둘이 다르므로 mock 값을 그대로 기대값으로 쓰면 어긋남. (`.claude/rules/dto-guide.md`)

- 기대값은 mock 응답을 **해당 응답 DTO 클래스로 변환한 값**에서 가져옴. 변환이 없는 필드도 같은 경로를 씀 — 나중에 그 필드에 변환이 생겨도 테스트를 고치지 않아도 됨.
- 이를 위해 **테스트 파일(`test.tsx` · `*.test.ts` · `src/__test__/**`)만 `dto/`의 응답 DTO 클래스를 값 import할 수 있음.** 프로덕션 코드의 `shared/api` 밖 `dto/` import 금지는 그대로. (`.claude/rules/dto-guide.md` 「의존 규칙」)
- 변환 로직을 테스트에 되풀이하지 않음(`mockResponse.name.trim()` ❌). 변환 규칙의 정본은 DTO 생성자 하나.
- 요청 DTO는 테스트에서 `new`하지 않음. 컴포넌트가 `mutate`에 plain object(`Input` 모양)를 넘기므로 테스트도 화면 입력만 재현하면 됨.

```tsx
// src/modules/widgets/workspace/WorkspaceList/test.tsx
import { GetWorkspacesResponseDto } from "@api/dto/workspace";
import { workspacesResponse } from "@api/mock/responses/workspace";

const expected = new GetWorkspacesResponseDto(workspacesResponse);

it("워크스페이스 목록을 보여준다", async () => {
  render(<WorkspaceList />);

  expect(
    await screen.findByText(expected.workspaces[0].name),
  ).toBeInTheDocument();
});
```
