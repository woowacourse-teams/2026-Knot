---
name: create-story
description: 구현·설계를 마친 컴포넌트의 스토리북 스토리(`index.stories.tsx`)를 만든다. 컴포넌트가 가질 수 있는 상태를 스토리로 나누고, 각 상태를 언제 쓰는지 주석으로 맥락을 남긴다. "스토리 만들어줘", "스토리북 추가해줘", "create-story" 요청뿐 아니라, 컴포넌트를 새로 만들거나 props·상태·모양을 바꾸는 구현을 마쳤을 때 요청이 없어도 사용.
---

# 스토리 작성

스토리는 PR 리뷰어와 개발자가 **코드를 읽지 않고도 컴포넌트를 직접 조작해 보고 이해**하도록 만드는 문서다.
리뷰어에는 코드를 읽지 않는 기획자·디자이너도 포함된다. 각자 UI를 확인하려고 임시 playground 화면을 만들던 일을 스토리가 대신한다.

그래서 상태를 보여 주는 것만큼, 그 상태를 **언제·왜 쓰는지** 설명으로 남기는 것이 중요하다. **스토리 문서 페이지의 설명만 읽어도 맥락이 보여야 한다.**

## 언제 만드나

- 컴포넌트를 새로 만들거나 props·상태·모양을 바꾸는 작업이면, 요청이 없어도 구현을 마친 뒤 이 스킬로 스토리를 작성·갱신한다. 테스트처럼 구현의 일부다. (`CLAUDE.md` 작업 절차 4단계)
- 구현 중에 먼저 만들지 않는다. 구현·설계를 마친 뒤 만든다.
- 만든 스토리는 사람이 한 번 더 검토한다. 작성 후 스토리 목록을 보여 주고 확인받는다.
- API를 호출하는 컴포넌트(쿼리·뮤테이션 훅을 쓰는 위젯·피처)도 대상이다. 실제 서버 대신 msw가 응답한다.
  - 기본 응답은 `.storybook/preview.tsx`가 `shared/api/mock/handlers`(vitest와 같은 핸들러)로 깔아 둔다. 정상 상태 스토리는 따로 핸들러를 두지 않는다.
  - 빈 목록·에러·로딩처럼 응답에 따라 화면이 달라지는 상태는 스토리의 `parameters.msw.handlers`로 그 요청만 덮는다. 응답 값은 `shared/api/mock/responses`에서 가져오고 새로 지어내지 않는다.
  - 스토리마다 새 QueryClient가 만들어지고 재시도가 꺼져 있어, 에러 상태가 바로 보인다.

## 절차

1. **컴포넌트를 읽는다.** 대상 컴포넌트의 `index.tsx`, props 타입, JSDoc, `ui` 세그먼트를 읽는다. 구현에 쓴 Figma 링크를 확인한다. 링크를 모르면 개발자에게 묻는다.
2. **상태 목록을 뽑는다.** props와 분기(`variant`, `size`, `isLoading`, `disabled`, 빈 값, 긴 텍스트 등)에서 화면이 달라지는 경우를 나열한다.
   - 코드에 없는 상태를 지어내지 않는다.
   - 분기는 있는데 왜 있는지 알 수 없으면 추측해 적지 말고 사용자에게 묻는다.
3. **목록을 먼저 보여 준다.** 스토리 이름과 한 줄 설명을 사용자에게 보여 주고, 빠진 상태가 없는지 확인받은 뒤 작성한다.
4. **작성한다.** 컴포넌트 폴더 바로 아래 `index.stories.tsx`에 둔다. (`.claude/rules/segment-pattern.md` 「스토리 위치」)
5. **Figma 링크를 옮긴다.** 컴포넌트 JSDoc의 Figma `@see` 링크를 스토리 `parameters.design`으로 옮기고 컴포넌트에서는 `@see`를 지운다. 컴포넌트 JSDoc의 설명과 코드 주석은 스토리 설명과 겹쳐도 그대로 둔다. 컴포넌트에 스토리북을 가리키는 안내 문장은 쓰지 않는다.
6. **검증한다.** `pnpm build-storybook`과 `pnpm tsc`가 통과하는지 확인하고, `pnpm storybook`에서 확인할 스토리 목록을 알린다.

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
- 구현에 쓴 Figma 링크를 `meta.parameters.design`에 넣는다(`{ type: "figma", url }`). 링크를 모르면 개발자에게 묻는다. 설명 본문에는 링크를 다시 적지 않는다. url의 `&t=` 파라미터는 뺀다.
  - 링크가 여러 개면 대표 프레임은 meta에 둔다.
  - 상태가 명확한 프레임은 해당 스토리의 `parameters.design`에 둔다.
  - 부품 프레임이나 어느 스토리에 붙일지 애매한 프레임은 meta의 `design`을 배열로 바꿔 `{ name, type: "figma", url }`로 넣는다. 배열 첫 항목은 대표 프레임이고, `name`에는 Figma 프레임 이름을 쓴다. Design 탭에 이름 붙은 탭으로 보인다.
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
