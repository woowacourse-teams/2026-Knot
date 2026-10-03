import type { Meta, StoryObj } from "@storybook/react-webpack5";

import Divider from ".";

/**
 * 콘텐츠를 가로로 나누는 구분선이에요.
 *
 * - 글자 없이 쓰면 위아래 내용을 나눠요. 예: 녹음 카드의 녹음 정보와 안내 문구 사이
 * - `label`을 넘기면 선이 양쪽으로 갈라지고 그 사이에 글자가 들어가요. 두 선택지를 나눌 때 써요. 예: 워크스페이스 생성 및 참여 카드의 「또는」
 * - 부모 너비를 꽉 채우므로 폭은 쓰는 쪽이 정해요.
 */
const meta = {
  title: "Shared/Divider",
  component: Divider,
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1738",
    },
  },
  decorators: [
    (Story) => (
      <div style={{ width: "22.5rem" }}>
        <Story />
      </div>
    ),
  ],
} satisfies Meta<typeof Divider>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 글자 없는 구분선이에요. 위아래 내용을 나눌 때 써요. 예: 녹음 카드 */
export const Default: Story = {};

/** 두 선택지를 나눌 때 써요. 예: 워크스페이스 생성 및 참여 카드에서 만들기 버튼과 참여 버튼 사이의 「또는」 */
export const WithLabel: Story = {
  args: { label: "또는" },
};
