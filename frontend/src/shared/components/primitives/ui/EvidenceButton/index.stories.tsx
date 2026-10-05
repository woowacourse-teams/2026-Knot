import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { fn } from "storybook/test";

import EvidenceButton from ".";

/**
 * 답변 아래에서 그 답변의 찾은 기록을 여는 버튼이에요. 탐색 화면에서 근거가 있는 AI 답변 아래에 둬요.
 *
 * - 「기록 N개에서 찾았어요」로 답변의 근거가 된 기록 수를 알려요.
 * - 그 답변의 찾은 기록이 열려 있으면 `isOpen`으로 채워진 모양이 돼요. 스크린리더에도 눌린 상태로 알려요.
 */
const meta = {
  title: "Shared/EvidenceButton",
  component: EvidenceButton,
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2017-36200",
    },
  },
  args: {
    count: 2,
    isOpen: false,
    onClick: fn(),
  },
} satisfies Meta<typeof EvidenceButton>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 이 답변의 찾은 기록이 닫혀 있을 때예요. */
export const Default: Story = {};

/** 이 답변의 찾은 기록이 오른쪽 패널에 열려 있을 때예요. 채워진 모양으로 지금 열려 있음을 보여줘요. */
export const Open: Story = {
  args: { isOpen: true },
};
