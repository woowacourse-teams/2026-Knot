import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { MemoryRouter } from "react-router";

import SearchReferenceList from ".";

/**
 * AI 탐색 답변의 근거가 된 문서 목록이에요. 탐색 화면의 「찾은 문서」 구획에 써요.
 *
 * **동작 규칙**
 * - 문서는 관련도가 높은 순서로 위에서부터 놓여요.
 * - 카드를 누르면 원본 문서(노션 페이지)가 새 탭에서 열려요.
 * - 아직 탐색 API를 연결하지 않아 예시 문서 두 개를 그려요. 그중 하나는 제목이 아주 긴 문서예요.
 *
 * **디자인 원본**: [찾은 문서](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=506-7219) ·
 * [찾은 문서 목록](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=606-2921)
 */
const meta = {
  title: "Search/SearchReferenceList",
  component: SearchReferenceList,
  parameters: { layout: "padded" },
  decorators: [
    (Story) => (
      <MemoryRouter>
        <Story />
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof SearchReferenceList>;

export default meta;

type Story = StoryObj<typeof meta>;

/** AI가 답변의 근거로 문서를 찾아온 상태예요. */
export const Default: Story = {};
