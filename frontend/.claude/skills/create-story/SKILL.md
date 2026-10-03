---
name: create-story
description: 구현·설계를 마친 컴포넌트의 스토리북 스토리(`index.stories.tsx`)를 만든다. 테스트 코드에 있는 시나리오를 기준으로 컴포넌트의 상태를 스토리로 나누고, 각 상태를 언제 쓰는지 주석으로 맥락을 남긴다. "스토리 만들어줘", "스토리북 추가해줘", "create-story" 요청뿐 아니라, 컴포넌트를 새로 만들거나 props·상태·모양을 바꾸는 구현을 마쳤을 때나 컴포넌트에 Figma `@see`가 남아 있을 때 요청이 없어도 사용.
---

# 스토리 작성

스토리는 PR 리뷰어와 개발자가 **코드를 읽지 않고도 컴포넌트를 직접 조작해 보고 이해**하도록 만드는 문서다.
리뷰어에는 코드를 읽지 않는 기획자·디자이너도 포함된다. 각자 UI를 확인하려고 임시 playground 화면을 만들던 일을 스토리가 대신한다.

그래서 상태를 보여 주는 것만큼, 그 상태를 **언제·왜 쓰는지** 설명으로 남기는 것이 중요하다. **스토리 문서 페이지의 설명만 읽어도 맥락이 보여야 한다.**

## 언제 만드나

- 컴포넌트를 새로 만들거나 props·상태·모양을 바꾸는 작업이면, 요청이 없어도 구현을 마친 뒤 이 스킬로 스토리를 작성·갱신한다. 테스트처럼 구현의 일부다. (`CLAUDE.md` 작업 절차 4단계)
- 구현 중에 먼저 만들지 않는다. 구현·설계와 테스트를 마친 뒤 만든다.
- 만든 스토리는 사람이 한 번 더 검토한다. 작성 후 스토리 목록을 보여 주고 확인받는다.
- API를 호출하는 컴포넌트(쿼리·뮤테이션 훅을 쓰는 위젯·피처)도 대상이다. 실제 서버 대신 msw가 응답한다.
  - 기본 응답은 `.storybook/preview.tsx`가 `shared/api/mock/handlers`(vitest와 같은 핸들러)로 깔아 둔다. 정상 상태 스토리는 따로 핸들러를 두지 않는다.
  - 빈 목록·에러·로딩처럼 응답에 따라 화면이 달라지는 상태 중 테스트에 시나리오가 있는 것만 스토리의 `parameters.msw.handlers`로 그 요청을 덮어 보여 준다. 응답 값은 `shared/api/mock/responses`에서 가져오고 새로 지어내지 않는다.
  - 스토리마다 새 QueryClient가 만들어지고 재시도가 꺼져 있어, 에러 상태가 바로 보인다.

## 어떤 상태를 스토리로 두나

스토리는 **테스트 코드에 작성된 시나리오를 기준으로** 만든다. 테스트가 통과했다는 것은 그 로직이 실제 코드에 있고 동작한다는 뜻이기 때문이다.

- **로직이 만드는 상태**(API 응답별 화면, 로딩·에러, 사용자 상호작용 결과, 타이머 등)는 vitest 테스트(`src/**/test.tsx`, `src/**/*.test.ts`, `src/__test__/**`)에 그 시나리오가 있을 때만 스토리로 둔다.
  - 테스트가 없는 로직 상태는 스토리로 만들지 않는다. 그 상태가 필요하면 스토리보다 **테스트를 먼저 쓴다**(`.claude/rules/test-strategy.md`). 테스트가 통과한 뒤 스토리를 더한다.
- **다른 화면으로 이동만 하는 결과**는 테스트가 있어도 스토리로 두지 않는다. 이동한 화면은 이 컴포넌트가 그리는 화면이 아니므로 자리표시 문구로 흉내 내지 않는다. 이동은 테스트로 검증하는 것으로 충분하다.
- **코드에 처리 분기가 없어 다른 스토리와 화면이 같은 상태**는 스토리로 두지 않는다. 예: 실패해도 버튼만 다시 열려 `Default`와 똑같이 보이는 경우.
- **로직 없이 props만으로 모양이 바뀌는 프레젠테이션 컴포넌트**(`shared/components/primitives` 등)의 `variant`·`size` 같은 props 조합은 테스트 대상이 아니므로 테스트 없이 스토리로 둔다.
- 어느 경우든 코드에 없는 상태를 지어내지 않는다.
- 컴포넌트가 처음 그려지는 기본 모양(`Default`)은 둔다. 남긴 스토리마다 근거가 되는 테스트 파일과 시나리오를 찾을 수 있어야 한다. 스토리 코드에 테스트 경로 주석은 달지 않는다.

## 절차

1. **컴포넌트를 읽는다.** 대상 컴포넌트의 `index.tsx`, props 타입, JSDoc, `ui` 세그먼트를 읽는다. 구현에 쓴 Figma 링크를 구현 요청 프롬프트·Issue·컴포넌트 `@see`에서 찾아 그대로 쓴다. 다시 묻지 않고, 어디에도 없을 때만 개발자에게 묻는다.
2. **상태 목록을 뽑는다.** props와 분기(`variant`, `size`, `isLoading`, `disabled`, 빈 값, 긴 텍스트 등)에서 화면이 달라지는 경우를 나열한다.
   - 코드에 없는 상태를 지어내지 않는다.
   - 분기는 있는데 왜 있는지 알 수 없으면 추측해 적지 말고 사용자에게 묻는다.
   - **테스트 시나리오를 확인한다.** 로직이 만드는 상태마다 그 시나리오가 있는 테스트(통합 `test.tsx`, 유틸 `*.test.ts`, E2E `src/__test__/**`)를 찾아 짝지운다. 위 「어떤 상태를 스토리로 두나」에 따라 테스트가 없는 상태, 이동만 하는 결과, 다른 스토리와 화면이 같은 상태는 목록에서 뺀다.
   - 테스트가 없어 뺀 로직 상태는 따로 적어 두고, 스토리 대신 테스트를 먼저 쓰자고 사용자에게 알린다.
3. **목록을 먼저 보여 준다.** 스토리 이름과 한 줄 설명, 근거 테스트(파일과 시나리오 이름)를 사용자에게 보여 주고, 빠진 상태가 없는지 확인받은 뒤 작성한다. 테스트가 없어 뺀 상태도 함께 보여 준다.
4. **작성한다.** 컴포넌트 폴더 바로 아래 `index.stories.tsx`에 둔다. (`.claude/rules/segment-pattern.md` 「스토리 위치」)
5. **Figma 링크를 옮긴다.** 컴포넌트(`index.tsx`와 `ui/` 서브 컴포넌트) JSDoc에 Figma `@see`가 있으면 스토리 `parameters.design`으로 옮기고 컴포넌트에서는 `@see`를 지운다. 개발자가 습관대로 넣은 `@see`도 스토리를 작성·갱신할 때마다 이렇게 정리한다. 스토리가 없는 pages·유틸의 `@see`는 그대로 둔다. 컴포넌트 JSDoc의 설명과 코드 주석은 스토리 설명과 겹쳐도 그대로 둔다. 컴포넌트에 스토리북을 가리키는 안내 문장은 쓰지 않는다.
6. **검증한다.** `pnpm build-storybook`과 `pnpm tsc`가 통과하는지 확인하고, `pnpm storybook`에서 확인할 스토리 목록을 알린다.

## 스토리를 지울 때

기존 스토리가 위 기준에 맞지 않아 지우면 함께 정리한다.

- 그 스토리에만 쓰인 msw 핸들러·헬퍼·데코레이터·`parameters` 값·import를 지운다.
- 그 스토리만 가던 화면의 자리표시 라우트(`<p>…이동했어요</p>`)를 지운다. 남은 스토리에서 직접 눌러 갈 수 있는 화면의 라우트는 남긴다.
- meta 설명에서 지운 스토리를 가리키는 문장은 사실에 맞게 고친다. 「테스트로 검증됨」 같은 문장을 덧붙이지 않는다. 컴포넌트의 동작 규칙 설명은 코드와 맞으면 그대로 둔다.
- 지운 스토리에만 있던 `parameters.design` 링크는 meta의 `design` 배열로 옮겨 잃지 않게 한다.

## 작성 규칙

- `title`은 코드 레이어가 아니라 **리뷰어가 찾는 기준**으로 적는다. 리뷰어는 「녹음 바」, 「초대 카드」처럼 영역으로 찾지 `widgets`·`primitives` 같은 코드 설계 용어로 찾지 않는다.
  - `shared/components`(primitives·composites) → `Shared/{컴포넌트}`. 예: `Shared/Button`, `Shared/DockablePanel`
  - `primitives/layout` → `Shared/Layout/{컴포넌트}`. 예: `Shared/Layout/Stack`
  - `modules`(widgets·features) → `{도메인}/{컴포넌트}`. 도메인 폴더 이름을 PascalCase로 쓰고 widgets와 features를 구분하지 않는다. 예: `Workspace/WorkspaceInviteCard`, `Recording/RecorderBar`, `Auth/GithubLoginButton`
  - 파일을 다른 레이어로 옮겨도 도메인이 같으면 title은 바꾸지 않는다.
- `meta`의 JSDoc이 문서 페이지의 설명이 된다. 코드를 읽지 않는 사람도 이것만 보고 이해하도록 아래를 담는다.
  - 무엇을 하는 컴포넌트이고 화면의 어디에 쓰는지
  - 어떤 모양·상태를 언제 고르는지 (실제 화면의 예와 함께)
  - 동작 규칙과 그렇게 만든 이유
- 구현에 쓴 Figma 링크를 `meta.parameters.design`에 넣는다(`{ type: "figma", url }`). 링크를 찾는 곳은 절차 1단계와 같다. 설명 본문에는 링크를 다시 적지 않는다. url의 `&t=` 파라미터는 뺀다.
  - 링크가 여러 개면 대표 프레임은 meta에 둔다.
  - 상태가 명확한 프레임은 해당 스토리의 `parameters.design`에 둔다.
  - 부품 프레임이나 어느 스토리에 붙일지 애매한 프레임, 스토리로 두지 않은 상태의 프레임은 meta의 `design`을 배열로 바꿔 `{ name, type: "figma", url }`로 넣는다. 배열 첫 항목은 대표 프레임이고, `name`에는 Figma 프레임 이름을 쓴다. Design 탭에 이름 붙은 탭으로 보인다.
  - 스토리의 `parameters.design`은 meta의 값을 덮어쓴다. 대표 프레임을 보여 줘야 하는 스토리(예: `Default`)에는 따로 두지 않는다.
- 스토리 하나에 상태 하나. 스토리마다 JSDoc 한 줄로 **언제 쓰는지**를 적고, 가능하면 실제 화면의 예를 든다. (예: `/** 되돌릴 수 없는 동작에 써요. 예: 워크스페이스 나가기 */`)
- 기본값은 `meta.args`에 두고, 각 스토리는 달라지는 `args`만 덮는다.
- 선택지가 정해진 prop은 `argTypes`에 `control`과 `options`를 둬 리뷰어가 바꿔 볼 수 있게 한다.
- 여러 크기·모양을 나란히 비교할 때만 `render`를 쓴다.
- 라벨과 예시 텍스트는 실제 서비스 문구를 쓴다. `Button`, `Lorem ipsum` 같은 자리 표시 문구를 쓰지 않는다.

## 예시

`src/shared/components/primitives/ui/Button/index.stories.tsx`를 참고한다.

```tsx
import type { Meta, StoryObj } from "@storybook/react-webpack5";

import Button from ".";

/**
 * 액션을 실행하는 기본 버튼이에요.
 *
 * - 겉모양은 `variant`로, 상태는 `isLoading`·`disabled`로 정해요.
 */
const meta = {
  title: "Shared/Button",
  component: Button,
  parameters: {
    design: [
      {
        name: "Button/CTA/M",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=511-284",
      },
      {
        name: "Button/CTA/L",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=422-440",
      },
    ],
  },
  args: { children: "워크스페이스 만들기", variant: "filled" },
} satisfies Meta<typeof Button>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 방금 끝난 일을 알릴 때 써요. 예: 초대 코드를 복사한 뒤의 「복사됨」 */
export const Accent: Story = {
  args: { variant: "accent", children: "복사됨" },
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=484-4926",
    },
  },
};
```
