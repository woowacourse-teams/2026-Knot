import type { Meta, StoryObj } from "@storybook/react-webpack5";

import Chip from ".";

/**
 * 짧은 정보를 담는 작은 칩이에요. 예: 녹음 길이 `25분`
 *
 * - 받은 글자를 그대로 보여줘요. 녹음 길이처럼 값을 문구로 바꿔야 하면 쓰는 쪽에서 바꾼 결과를 넘겨요.
 *   예: `<Chip>{formatDurationFromSeconds(durationSeconds)}</Chip>`
 * - 다른 요소 옆에 붙여 쓰므로 간격은 쓰는 쪽이 정해요.
 * - 글자가 길어도 줄을 바꾸지 않아요.
 *
 * **디자인 원본**: [Chip/Duration](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2062-7288)
 */
const meta = {
  title: "Shared/Chip",
  component: Chip,
  args: {
    children: "25분",
  },
} satisfies Meta<typeof Chip>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 녹음 길이를 보여줄 때예요. 예: `25분` */
export const Default: Story = {};

/** 1시간이 넘는 녹음이에요. 글자가 길어져도 한 줄을 지켜요. 예: `1시간 12분` */
export const LongText: Story = {
  args: { children: "1시간 12분" },
};
