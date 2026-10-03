import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { fn } from "storybook/test";

import SourceButton from ".";

/**
 * 답변의 근거 문서를 여는 버튼이에요. 탐색 화면에서 AI 답변 아래에 둡니다.
 *
 * - 지금 열려 있으면 `isSelected`로 채워진 모양이 돼요. 스크린리더에도 눌린 상태로 알려요.
 * - 라벨 문구는 쓰는 쪽이 정해 `children`으로 넘겨요.
 */
const meta = {
  title: "Shared/SourceButton",
  component: SourceButton,
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1170-697",
    },
  },
  args: {
    children: "근거 문서 3개",
    isSelected: false,
    onClick: fn(),
  },
} satisfies Meta<typeof SourceButton>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 근거 문서가 닫혀 있을 때예요. */
export const Default: Story = {};

/** 근거 문서가 열려 있을 때예요. 채워진 모양으로 지금 열려 있음을 보여줘요. */
export const Selected: Story = {
  args: { isSelected: true },
};
